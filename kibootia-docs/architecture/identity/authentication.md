# Authentication

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** verified (CI run 42780048416, 2026-11-10). Keycloak 26.8.2 on PostgreSQL 17.11 runs in `compose.yaml`; OpenTofu applies the realm (`infra/keycloak`); the Next.js server signs a user in with Authorization Code and PKCE, refreshes the session (on demand as well, through `/auth/refresh`) and signs out; Playwright tests against the real Keycloak cover sign-in, self-registration with email verification and invitations. Registration, email-verification enforcement and invitations are milestone 3; the administration client's audience scope is described below.

Decisions: [ADR 0006](../../decisions/0006-keycloak-oidc-identity.md), [ADR 0007](../../decisions/0007-spring-security-resource-server.md), [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md), [ADR 0026](../../decisions/0026-nextjs-bff-session.md), [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md), [ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md), [ADR 0045](../../decisions/0045-keycloak-operations.md).

## Responsibilities

- **Keycloak** authenticates users, issues tokens using OpenID Connect, sends identity email (invitation, verification, password reset) and owns the user lifecycle. Its data lives in its own PostgreSQL. The account lifecycle is in [user lifecycle](user-lifecycle.md).
- **The Next.js server** is the backend-for-frontend ([glossary](../../shared/glossary/backend-for-frontend.md)). It runs the login, holds the tokens, gives the browser only a session cookie and forwards browser API calls through one `/api` proxy ([frontend](../frontend.md)).
- **The Spring backend** is a [resource server](../../shared/glossary/resource-server.md): it accepts bearer access tokens and validates them ([token validation](token-validation.md)). It does not authenticate users or store passwords.
- **The user-administration module** calls the Keycloak Admin API to manage the user lifecycle. That requires a confidential service-account client, so the backend does hold one client secret for that purpose; the resource-server function itself still needs none.

## The administration client

The backend calls the Admin API with the `kibootia-admin` service-account client, which holds `view-users`, `query-users` and `manage-users` and nothing else. Keycloak 26.8 refuses an Admin API call unless the token's audience is the realm's admin API address, which the built-in `kc-admin-api` client scope adds, so that scope is a default scope of the client ([infra/keycloak/base/main.tf](../../../infra/keycloak/base/main.tf)). Without it every call is a 401, which the backend reports as 503 (administration and the first sign-in of each new user). `KeycloakAdminAdapterIT` runs against a real Keycloak and shows both the working account and the refusal of a client that holds fewer roles. See [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md).

## Sign-in flow

1. The browser asks the Next.js server for a protected page. With no session, the server starts the OpenID Connect Authorization Code flow with PKCE, as a confidential client.
2. Keycloak authenticates the user and returns to the Next.js callback. The server checks state, nonce and the PKCE verifier, and exchanges the code for tokens.
3. The server stores the session in encrypted HttpOnly, SameSite cookies (`Secure` over https; [how the session is built](#how-the-session-is-built)). The browser never receives a token.
4. The server calls Spring with the access token. The token must carry the backend's audience.
5. Expiring sessions are refreshed on the server, once at a time per request. If the refresh fails, the user signs in again.
6. Signing out ends the Keycloak session as well as the cookie.

## How the session is built

- **Format.** The session is one encrypted token (JWE, `A256GCM`, a key derived with HKDF from `SESSION_SECRET`) holding the access, refresh and ID tokens and their expiry times. It is split into cookies named `kibootia_session.<n>` of at most 3,600 bytes each, at most eight, so about 28 KB. All are `HttpOnly` and `SameSite=Lax`; `Secure` is set when `PUBLIC_BASE_URL` is `https`. There is one key and no rotation: changing `SESSION_SECRET` signs everyone out.
- **Lifetime.** The session ends at most ten hours after sign-in, however often it is refreshed. The ID token is kept in the cookie so that sign-out needs no confirmation page.
- **Refresh.** A session is refreshed 30 seconds before its access token expires, once at a time per refresh token within one Next.js process, in the middleware for pages (`proxy.ts`) and in the API proxy. A failed refresh clears the cookies and forces sign-in. `POST /auth/refresh` forces a refresh, which the unverified-email notice uses.
- **Sign-out** is initiated by the application (front-channel). It clears the cookies and sends the browser to Keycloak's end-session address with the ID token as a hint. There is no back-channel logout, so a session ended in Keycloak reaches the cookie only at the next refresh.
- **CSRF.** Every method except `GET` and `HEAD` needs an `Origin` equal to `PUBLIC_BASE_URL` and the header `x-kibootia-request`. `OPTIONS` is answered with 405, so no cross-origin preflight is ever granted.

Library and session store: `openid-client` plus a thin tested module, with the encrypted cookie as the store and a server-side store as the upgrade path for instant revocation ([ADR 0026](../../decisions/0026-nextjs-bff-session.md)). State-changing requests are protected against CSRF: they must carry a matching `Origin` header and a required custom header ([ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md)).

## Local environment

A local Keycloak runs in Docker Compose with its own PostgreSQL. Its realm is version-controlled and applied by OpenTofu, which runs as a one-shot container ([ADR 0045](../../decisions/0045-keycloak-operations.md)): a shared base (the Next.js client, the backend audience mapper, the least-privilege service-account client for administration, registration enabled, email verification handled by Kibootia) plus a local overlay (demo administrator and sample users). Secrets come from the generated `.env` ([ADR 0040](../../decisions/0040-local-development-topology-and-setup.md)); the configuration contains no real secrets, and production startup rejects demo data. The realm's contents are in `infra/keycloak/base/main.tf`.

## Production exposure (decided, not implemented)

Keycloak's login pages are served on `auth.<domain>` through the TLS reverse proxy. The admin console is blocked at the proxy and reached through an SSH tunnel. A production overlay of the realm configuration contains no demo data. Production SMTP settings are Keycloak's, from any provider ([open questions](../../decisions/open-questions.md)).

## Realm hardening

Built in the shared realm base (`infra/keycloak/base/main.tf`): brute-force detection (five failures, then a temporary lockout), a password policy of at least 12 characters that may not match the username or email, access tokens of five minutes, a signed-in session that idles out after 30 minutes and ends after 10 hours, and refresh-token rotation with a reuse tolerance of one so that two tabs refreshing at once do not sign the user out. Not built: a breached-password check, password history, required multi-factor authentication, and SMTP authentication or TLS ([architecture assessment](../architecture-assessment.md), B-4).

## Failure behavior

Missing, invalid or expired tokens receive 401 with a Problem Details body ([API conventions](../api/conventions.md)). Tokens issued to service accounts rather than users are rejected ([ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md)).

## Security considerations

The resource server needs only Keycloak's public signing keys. The Next.js client secret and the administration service-account secret are secrets and follow [secrets management](../../operations/security/secrets-management.md). The attack surface is listed in the [threat model](../../operations/security/threat-model.md).
