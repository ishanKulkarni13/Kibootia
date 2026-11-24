# 0063 BFF and edge hardening

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** items 1 to 6 and 8 built on branch `m-5-1-edge` (observed; CI run pending); item 7 waits for milestone 6. Observed in `frontend/src/proxy.ts`, `lib/security-headers.ts`, `lib/session.ts`, `lib/config.ts` and `lib/bff/proxy.ts`.

## Context

The Next.js server is the only thing a browser talks to ([ADR 0026](0026-nextjs-bff-session.md), [ADR 0042](0042-bff-api-proxy-and-csrf.md)), so its hardening is the application's edge. It already does the hard parts: the tokens never reach the browser, state-changing requests need a matching `Origin` and a custom header, and the proxy's upstream is fixed. The [assessment](../architecture/architecture-assessment.md) found what is missing (B-1 to B-3, E-3):

- no Content-Security-Policy and no HSTS; the session cookie has no `__Host-` prefix;
- the `/api` proxy has no timeout and no body-size limit, forwards headers by deny-list, and does not pass the client address, so the backend's audit and rate limiting see only the Next.js server;
- one session key with no rotation; sign-out is front-channel only;
- the frontend configuration falls back to localhost values, so a production start with a missing variable runs silently on HTTP localhost.

## Decision

1. **Security headers.** A Content-Security-Policy with a per-request nonce set in the middleware (every page is dynamic because it reads the session, so nonces cost nothing), `Strict-Transport-Security` when `PUBLIC_BASE_URL` is `https`, and `Cross-Origin-Opener-Policy`. The CSP starts in report-only mode for one release, then enforces.
2. **Cookie prefix.** When the base URL is `https`, session and login cookies are named with the `__Host-` prefix (`Secure`, `Path=/`, no `Domain`). Over local `http` the names stay as they are, because the prefix requires `Secure`.
3. **Proxy limits.** A timeout on the upstream call (default 30 seconds, longer for uploads) that returns Problem Details with 504, and a maximum request body equal to the backend's multipart limit, enforced before streaming.
4. **Header allow-list.** The proxy forwards only the request headers the API needs (content type and length, accept headers, `If-Match`, `If-None-Match`, the user agent), replacing the deny-list.
5. **Client address.** The proxy sends the client address in `X-Forwarded-For`, taken from the platform's trusted proxy chain (a setting for the number of trusted hops), and the backend is configured to trust it only from the Next.js server. Audit entries and the rate limiter then see the person, not the proxy. The threat model gains an entry for header spoofing.
6. **Session key ring.** `SESSION_SECRET` becomes an ordered list: the first key seals, all keys open, with a key identifier in the token's header. Rotation then needs no sign-out of everyone.
7. **Sign-out.** Front-channel sign-out stays. Back-channel logout needs server-side state to know which cookie to refuse, which the cookie-only design does not have. It is therefore tied to the server-side session store recipe planned for milestone 6, not built alone. The revocation delay stays at most one access-token lifetime (five minutes), because every refresh asks Keycloak.
8. **Fail fast in production.** At server start, when `NODE_ENV` is `production`, a missing `PUBLIC_BASE_URL`, issuer, API address or secret stops the server with a named message, as the backend already does ([ADR 0032](0032-secrets-from-environment-and-files.md)). The localhost defaults apply only outside production.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Set the headers at the reverse proxy only | Fine in the reference deployment, but the application would be unprotected behind any other front; keeping them in the app is portable |
| Keep the deny-list and extend it | A deny-list passes whatever it does not know; an allow-list fails safe |
| A server-side session store now | A new stateful component; the cookie design is a documented choice and the store is a recipe ([ADR 0026](0026-nextjs-bff-session.md)) |
| Trust any `X-Forwarded-For` | Lets a caller choose the address that is audited and rate-limited |

## Consequences

- A nonce-based CSP can break third-party scripts and inline styles in shadcn components; the report-only release is for finding those.
- The `__Host-` names change the cookie names between local and production, so tests and the data inventory name both.
- Trusting forwarded addresses is a deployment decision: the number of hops is documented in the deployment guide.

accepted on the owner's delegation of 2026-11-25. Delivered in [milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md), except item 7, which waits for milestone 6.

**Changes made while building** (2026-11-25):

- **Item 1.** The policy is built and starts in report-only mode, as decided (`CSP_MODE`: `report-only` by default, `enforce`, `off`). The middleware now runs for every page, not for a list of paths: the matcher excludes only the BFF routes, the API proxy and the files Next.js serves, because a page without the middleware has no nonce. Whether the policy blocks anything it should not is visible in the browser's console; there is no report endpoint, because an unauthenticated endpoint that logs what it is sent is a way to fill the log. The step from report-only to enforcing is the one-line `CSP_MODE=enforce`, to be taken after a release has shown nothing is reported. Styles keep `'unsafe-inline'`, because components position elements with inline `style` attributes, which a nonce cannot cover. HSTS is sent only when `PUBLIC_BASE_URL` is https.
- **Item 2.** Over https the cookies are `__Host-kibootia_session.<n>` and `__Host-kibootia_login`; over http, which only local development uses, the names are unchanged. A session is read only from the cookie of its own mode, so a plain cookie cannot stand in for a prefixed one. Everyone is signed in again once, when a deployment moves to https.
- **Item 3.** `API_PROXY_TIMEOUT_MS` (30 s) and `API_PROXY_UPLOAD_TIMEOUT_MS` (120 s, for `multipart/form-data`) end a call with 504 `bff.upstream_timeout`. `API_PROXY_MAX_BODY_BYTES` (6 MiB, the API's 5 MB upload limit plus the envelope) refuses a declared length over the limit with 413 before the session is read, and stops a streamed body that passes it. The timeout also ends a response that is still streaming after that long, which is acceptable for the sizes served today.
- **Item 4.** The allow-list is `accept`, `accept-encoding`, `accept-language`, `content-type`, `if-match`, `if-modified-since`, `if-none-match` and `user-agent`. The body's length is not forwarded, as before: the body is streamed.
- **Item 5.** `TRUSTED_PROXY_HOPS` (default 0) says how many proxies in front of the server are trusted to append the client's address to `X-Forwarded-For`; the proxy passes on the address that many places from the right and nothing else, and only if it looks like an address. With 0 it passes none, and the API sees the server's address. **Not built: the backend side.** The audit log has no address column and the rate limiter is per user, so there is nothing yet that reads the address; trusting it in Spring (`server.forward-headers-strategy`) is left to the deployment of milestone 6 and the milestone that adds an address to audit entries.
- **Item 6.** `SESSION_SECRET` is a list separated by commas, newest first. The first key seals, every key opens, and the token's header carries a short identifier of the key that sealed it. A token that was sealed before this change has no identifier and is opened by trying each key, so no one is signed out by the upgrade.
- **Item 8.** In production (`NODE_ENV=production`) a missing `PUBLIC_BASE_URL`, `KEYCLOAK_ISSUER`, `API_BASE_URL`, `SESSION_SECRET` or `KEYCLOAK_WEB_CLIENT_SECRET`, or an empty one, stops the server with a message that names the setting and no value. The `apps` Compose profile sets all of them.

## Related

[Architecture assessment](../architecture/architecture-assessment.md) (B-1 to B-3, E-3), [authentication](../architecture/identity/authentication.md), [threat model](../operations/security/threat-model.md), [ADR 0026](0026-nextjs-bff-session.md), [ADR 0042](0042-bff-api-proxy-and-csrf.md).
