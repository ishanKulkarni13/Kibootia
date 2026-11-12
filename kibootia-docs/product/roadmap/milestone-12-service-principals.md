# Milestone 12: Service Principals and Identity Mapping

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists. Machine tokens are refused today by decision ([ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md)).

A phase of the [authorization roadmap](authorization-roadmap.md). Decision: draft [ADR 0055](../../decisions/0055-authorization-decision-contract.md) (point 7); an ADR on service principals, which replaces the machine-client paragraph of ADR 0044, is written when the phase starts.

## 1. Objective

Give automated callers an identity of their own with only the permissions they need, and stop inferring who a caller is from the shape of a Keycloak token. After this phase no code path holds every permission, and a second identity provider could be added without touching authorization.

## 2. Requirements covered

AUTH-018 (service accounts), AUTH-035, AUTH-039. Security findings S5 and S6. No reference scenario: neither Timetia nor indent-edi documents a machine caller, so the scenarios are written from the requirement text.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| `platform/security/UserTokenValidator` | Replaced by a check against registered service principals |
| `platform/authorization/Authorization.caller()` | Asks one mapping component for the principal instead of parsing `sub` as a UUID |
| `Authorization.runAsSystem` and its users (`DurableEventInterceptor`, `RetentionPurgeJob`, `ReconciliationJob`) | Enter a named service principal with declared grants; the all-powerful caller is removed |
| `identity/application/ProfileGate` and the profile store | Find a profile by issuer and external subject |
| `access` assignments | The subject of an assignment may be a service principal |
| `infra/keycloak/overlays/local` | A sample machine client for tests |

## 4. New functionality delivered

- Service principals: a stored identity with a name, the client it corresponds to, an enabled flag and role assignments. It has no profile and never signs in interactively.
- A machine token is accepted only if its client is a registered, enabled service principal, and then carries exactly that principal's grants.
- Built-in service principals for the foundation's own background work, each with the grants its job needs, seeded like the built-in roles.
- One mapping from a token to a principal, owned by `identity` behind a port, with the Keycloak implementation as the only one shipped.
- An administration API and screen to register, enable, disable and assign roles to a service principal.
- A switch for accepting external machine tokens, off by default. The built-in service principals for background work are always present.

## 5. Modules affected

`platform` (security and authorization), `identity`, `access`, `audit`, `files`, `notes` (their background handlers), the frontend `access` feature, and the Keycloak configuration under `infra/`.

## 6. Dependencies

Milestone 8 (grants). It does not depend on milestones 9 to 11 and can be built directly after milestone 8 if the owner wants the all-powerful caller removed sooner.

## 7. Database changes and migration strategy

1. In the `access` folder: a table for service principals; the assignment subject gains a type (user or service), filled with "user" for existing rows.
2. In the `identity` folder: the profile gains an issuer and an external subject, filled from the existing key and the configured issuer, with a unique index on the pair. The internal identifier does not change, so no other table is touched.
3. Built-in service principals and their grants are inserted at startup, as the built-in roles are.

All three are additive.

## 8. Security implications

- This phase changes token validation, the most security-sensitive code in the backend. Every accepted and refused token shape gets a test in `ResourceServerIT` before the validator changes.
- A machine caller is registered explicitly and never inferred. An unregistered client is refused, as every machine token is today.
- A service principal never inherits a human's permissions and cannot be given the administrator role.
- Removing the all-powerful caller can break background work that silently relied on it. Each job and each event handler gets a test that it succeeds with its declared grants and that it is refused one action beyond them.
- A wrong mapping from token to principal would confuse two identities. The mapping is tested with a second token shape that is not Keycloak's.

## 9. Automated tests required

- `ResourceServerIT`: a registered machine token accepted; unregistered, disabled and wrong-audience tokens refused; a user token unchanged.
- Each background job and durable event handler runs under its service principal; one refused-action test each.
- The mapping: a non-UUID subject and a second issuer resolve to the right profile; two issuers with the same subject stay distinct.
- Service principal administration: allowed and denied; the "only what you hold" rule; no administrator role.
- The migration: every existing profile is found by its issuer and subject; every user's access is unchanged.
- `IdentityGateIT` and `UserAdministrationIT` pass unchanged in outcome.
- The switch off: every machine token is refused, as today.
- `module-removal`: removing a module removes its built-in service principal cleanly.

## 10. Documentation updates

[Authentication](../../architecture/identity/authentication.md), [token validation](../../architecture/identity/token-validation.md), [authorization](../../architecture/identity/authorization.md), the [threat model](../../operations/security/threat-model.md), [goals and non-goals](../goals-and-non-goals.md) (machine clients are no longer a non-goal), a recipe for the switch, [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), the service-principal ADR and an implementation-decisions ADR.

## 11. Acceptance criteria

1. No code path grants every permission; `Caller.system` or its successor no longer exists.
2. Each background job and handler has a named principal, declared grants, and a passing refused-action test.
3. The decision point contains no reference to a Keycloak claim name or naming convention.
4. With the switch off, `ResourceServerIT` shows the same outcomes as today for machine tokens.
5. All of the above pass in CI.

## 12. Rollback and recovery

The schema changes are additive and filled deterministically. Turning the switch off refuses external machine tokens at once. If a background job fails after the change because a grant is missing, the fix is to add the grant to its built-in service principal; the failed work is retried by the existing outbox retry and lock mechanisms, so nothing is lost.

## 13. Usable state at the end of the phase

Users notice nothing. Operators can register a machine client and give it a narrow role. Every action taken by background work is attributed to a named principal in the decision records.
