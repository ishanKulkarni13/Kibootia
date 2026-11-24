# Architecture Assessment of 2026-11-24

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** not applicable (a dated assessment of commit `e0dcc29`, plus the fixes of branch `m-5-1`). It describes what the code and documents were on that day. Nothing here is an owner decision: every recommendation is a proposal that becomes real only through the draft ADRs [0057](../decisions/0057-consumer-owned-ports-and-declared-module-dependencies.md) to [0063](../decisions/0063-bff-and-edge-hardening.md) and [milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md), once the owner accepts them.

## Purpose and method

The owner returned to the project after a break and asked what is done, where the structure falls short, and what a professional enterprise would do differently, with a focus on **where data lives, who owns what, modularity, flexibility, extensibility, cohesion and coupling**.

Three reading passes covered the backend modules, the platform and its tests, and the frontend, infrastructure and CI. The authorization and architecture documents were read in full; the rest of the 56 ADRs were read where they bear on a finding. Every finding carries a confidence tag:

| Tag | Meaning |
|---|---|
| **V** | Verified in this round: the code was re-read, or a test was run (see [what this round fixed](#what-this-round-fixed)) |
| **R** | Read in the code by one of the reading passes, with a file reference; not re-read |
| **I** | Inferred from the code or the documents; needs a test or a measurement before it is trusted |

Query counts and per-request costs are readings, not measurements. No load test exists.

## Scorecard

| Dimension | Rating | In one line |
|---|---|---|
| Module boundaries | Strong | Modulith and ArchUnit enforce them, and the rules are shown to fire |
| Coupling | Adequate | Compile-time graph is clean; three runtime cycles hide behind ports in `platform` |
| Cohesion | Adequate | Small modules; `identity` is the exception, and two services duplicate logic |
| Data ownership, in code | Strong | No cross-module SQL; each module owns its tables |
| Data integrity, in the database | Weak | No foreign keys, a uniqueness rule only in code, unversioned event payloads |
| Extensibility | Adequate | Real extension points, but some are closed enums or have no implementation yet |
| Flexibility (configuration) | Weak | No production profile; many limits are constants; localhost defaults in the frontend |
| Transactions and resilience | Weak | External calls inside database transactions; no retry or breaker for Keycloak |
| Security | Adequate | Sound core; one high finding (fixed here); edge hardening missing |
| Observability | Weak | Traces and health only; no custom metrics, no log export |
| Testing | Strong | Allowed and denied tests per operation; gaps listed below |
| CI and supply chain | Weak | No coverage, SAST, dependency audit, SBOM or pinned actions |
| Documentation | Strong process, weak accuracy | Good discipline; dozens of status banners were stale (corrected here) |
| Production readiness | Not started | Milestone 6 is unbuilt |

## What is done

| Milestone | State |
|---|---|
| 0 Hygiene and guardrails; 1 Walking skeleton; 2 Platform conventions; 3 Identity and access administration; 4 Files | Built and verified in CI |
| 5 Example feature | Built and verified in CI. Its 31 end-to-end tests ran in CI for the first time on 2026-11-24 (run 45354022559), after three defects they exposed were fixed: a heading missing on the note error page, an alert colour on the edge of the contrast minimum, and two test-isolation faults |
| 6 Production readiness and first release | Not built. Nothing deploys; no release tag, changelog or licence |
| 7 to 17 Authorization | Drafts only; no code |

In place: modular monolith with enforced boundaries, Keycloak sign-in through a Next.js BFF, runtime-editable roles with a central decision point, a tenant-ready schema, an audit log, durable events with retry, locked jobs, retention purge, files with image processing, a removable example feature, and a CI job that proves removal. [Implementation status](../product/roadmap/implementation-status.md) has the evidence per capability.

## Data and ownership

Where everything lives is in the [data inventory](data/data-inventory.md); who may use what is in the [module catalogue](module-catalogue.md). In short: MySQL holds each module's tables, PostgreSQL holds Keycloak only, S3 holds objects, the session is an encrypted cookie in the browser, and some state is in process memory.

| ID | Finding | Evidence | Sure |
|---|---|---|---|
| D-1 | **No foreign keys anywhere**, including inside one module (`role_permission.role_id`, `role_assignment.role_id` → `role`). Cross-module links are IDs by design, but the same-module ones have no reason to be unchecked | No `FOREIGN KEY` in any migration | V |
| D-2 | **Email uniqueness is checked in code only.** `user_profile` has a non-unique index on `(tenant_id, email)`; two concurrent invitations of one address can both pass | `V202611101010__identity_create_user_profile.sql:28`; check in `UserAdministration` | V (index), R (race) |
| D-3 | **Event payloads are unversioned**, stored as JSON in `event_publication`. Renaming a field of a record that has undelivered events breaks their delivery. The handler key `Class#method` also changes when a class is renamed, so old idempotency marks stop matching | `DurableEventInterceptor.java:47`; no versioning anywhere | V |
| D-4 | **Exhausted failed events are never purged**, and nothing alerts on them. `failed-events` filters by `json_extract` on an unindexed column | `EventRetention`, `FailedEventAdministration` | R |
| D-5 | **Erasure leaves files behind when the files module is off.** `FileEvents` is not registered, so `UserErased` is never handled for files, and nothing replays it after the module is switched back on | `ConditionalOnFilesEnabled`; `FileEvents` | R |
| D-6 | **Per-instance state.** Rate-limit buckets and the Keycloak admin token live in each backend process; the refresh single-flight map lives in each Next.js process. With two instances every limit is doubled | `RateLimiter`, `KeycloakAdminAdapter`, `refresh.ts` | R |
| D-7 | **No backup or restore design across MySQL, PostgreSQL and the object store**, and until now no inventory of personal data | `compose.yaml`; docs | V |
| D-8 | **The tenant is a constant.** `TenantResolver` has no implementation; jobs hard-code `TENANTS = List.of(DEFAULT_TENANT)`; `event_publication` and `shedlock` have no `tenant_id`; no automated rule catches a native query without a tenant condition (S7 of the [security review](../temp/authorization-security-review.md)). Safe with one tenant, a leak waiting for the second | `RetentionPurgeJob.java:26`, `SecurityConfiguration.java:61` | R |
| D-9 | **Hand-written SQL in some adapters** (`JpaRoleStore`, `StoredPermissionSource`, `JpaUserProfileStore`) mixes with JPA. Fine, but each needs its tenant condition by hand (see D-8) | explorer reports | R |

## Modularity, cohesion and coupling

The compile-time structure is the strongest part of the project: no module imports another module's sub-package, and every cross-module call goes through an interface, record, constant or event.

| ID | Finding | Evidence | Sure |
|---|---|---|---|
| C-1 | **Three runtime cycles hide behind ports hosted in `platform`.** `identity` calls `access`, and `access` calls back into `identity` through `UserDirectory`; the same for `audit`; every module reaches `access` through the decision point. Spring Modulith cannot see these, because each edge points at `platform` | [module catalogue](module-catalogue.md#runtime-cycles-that-this-graph-does-not-show) | R |
| C-2 | **`platform` hosts contracts that belong to features:** `directory.UserDirectory` and its lifecycle enum (identity), `files.FileCategory` with image rules (files), `ratelimit.RateLimitCategory` listing feature buckets, `security.RequestGate` (identity), and `config` naming identity and files property keys. A technical kernel should not change when a feature does | Explorer reports; `FileCategory.java`, `UserDirectory.java` read | V (two), R |
| C-3 | **Only `platform` declared `allowedDependencies`**, so the graph between business modules was whatever the code happened to do | `package-info.java` files | V. **Fixed here** |
| C-4 | **`identity` tells `access` to drop an erased user's assignments** (`removeAll`), so identity knows that access holds data about users. This is orchestration of the user lifecycle by the module that owns the user, and it is acceptable: the alternative, `access` listening to identity's `UserErased`, would add the edge `access` → `identity` next to the existing `identity` → `access` and create a module cycle. Keep, and keep it documented | `UserEvents.java:105` | V. **Not a defect** |
| C-5 | **A rule coded twice:** "the administrator role means every permission" is in `RoleAssignmentService.permissionsOf` and in `StoredPermissionSource` (raw SQL) | explorer reports | R |
| C-6 | **`identity/application` mixes** use cases, ports, events and listeners in 18 files; `UserAdministration` has 11 constructor dependencies | counts | R |
| C-7 | **`AvatarService` and `NoteAttachmentService` duplicate** the `files.disabled` constant, `matches()`, `discardQuietly` and the sealed view types. The guide's way to add a feature is "copy `NoteAttachmentService`", so each new feature copies the duplication | explorer reports; [guide](../engineering/guides/add-a-feature.md) | R |
| C-8 | **Frontend:** `identity` and `access` import each other; `components/` imports from `features/` (the reverse of the usual direction); about 20 permission names are string literals; one central message map covers every module's errors; `modules.ts` registers only `notes`, so the removal script also edits tagged lines in four other files | explorer reports | R |

**Cohesion verdict.** Modules are small and each has one purpose. `identity` is the one that has grown (35 files, 2,617 lines), because it holds the user lifecycle, the request gate, avatars and the Keycloak adapter.

## Transactions and resilience

| ID | Finding | Evidence | Sure |
|---|---|---|---|
| T-1 | **S3 is called inside database transactions.** `FileRetention.purge` is `@Transactional` and deletes up to 20 batches of objects inside it; `FileEvents` does the same in its handler's transaction. This contradicts the module's own rule ("no database transaction is ever open while the storage server is being called", `FileService`) | `FileRetention.java:57-67` | V |
| T-2 | **Keycloak is called inside database transactions.** `DurableEventInterceptor` runs every handler in a `REQUIRES_NEW` transaction that holds the idempotency mark, so `UserEvents` calls Keycloak with a connection and a row lock held. A slow Keycloak holds database connections | `DurableEventInterceptor.java:59-76`, `UserEvents.java:54-104` | V |
| T-3 | **The Keycloak adapter has timeouts (3 s and 10 s) but no retry, circuit breaker or bulkhead.** One 401 retry is the only one. The compensating delete when an invite fails is logged on failure and leaves an orphan Keycloak account | `KeycloakAdminAdapter` | R |
| T-4 | **A request costs at least two reads and one transaction in the request gate**, before the controller; every permission check is a fresh three-table query with no per-request memoisation (one statement per decision, measured since branch `m-8` by `DecisionQueryCountIT`); `/me` costs about nine queries. Acceptable now; a request-level baseline does not exist (OQ-70) | `ProfileGate`, `StoredPermissionSource` | R, I |
| T-5 | **The request gate fails open on a wiring mistake:** `RequestGateFilter` is registered only if a `RequestGate` bean exists, so a missing bean silently skips the disabled-user check (S11) | `SecurityConfiguration.java:65-68` | R |
| T-6 | **`event_publication.serialized_event` is `VARCHAR(4000)`**: an event that grows past it fails to store | migration | R |

## Flexibility and extensibility

Real extension points exist and are used: `PermissionProvider`, `RetentionPurgeable`, `@DurableEventListener`, `FileInspector` (`@ConditionalOnMissingBean`), the decision-point port and `IdentityProviderAdmin`. Where they are closed:

| ID | Finding | Evidence | Sure |
|---|---|---|---|
| E-1 | **`RateLimitCategory` is a closed enum** in `platform`, so a new feature that wants its own bucket edits `platform` | explorer reports | R |
| E-2 | **`ResourcePolicy` has no implementation, and the first policy that supports a record decides.** The first module to implement one can silently widen what roles say (S3 of the security review) | `Authorization.java:64-84` | R |
| E-3 | **No production configuration.** There is no `application-prod` profile, no connection-pool settings, and the frontend falls back to localhost values when settings are missing, so a mis-configured production starts silently on HTTP localhost | `config.ts`, `application.properties` | R |
| E-4 | **Constants that should be settings:** JWK and S3 timeouts, retry batch size, purge batch limits, per-tenant job lists, image limits, audit limits | list in the [configuration reference](../operations/configuration/application-configuration.md) to extend | R |
| E-5 | **Only infrastructure-backed modules have a switch** (ADR 0024). Consistent, but identity, access and audit cannot be left out by a fork that brings its own identity model | ADR 0024 | V |

## Security and the edge

The core is sound: deny by default, audience validation, a central decision point, every use case checked (rule 7), allowed and denied tests. Findings:

| ID | Finding | Evidence | Sure |
|---|---|---|---|
| S-1 | **Role assignment let a caller take roles they could not create** (the review's S1, High). `access.roles.assign` alone allowed giving anyone, including yourself, every role except administrator | `RoleAssignmentService`; two new tests fail without the fix (200 and 201 instead of 403) | V. **Fixed here** |
| B-1 | **No Content-Security-Policy and no HSTS** from the Next.js server; the session cookie has no `__Host-` prefix | `next.config.ts` | R |
| B-2 | **The `/api` proxy has no timeout and no body limit,** forwards headers by deny-list (anything not listed passes), and does not forward the client address, so the backend sees only the Next.js server for audit and rate limiting | `lib/bff/proxy.ts` | R |
| B-3 | **One session key, no rotation;** changing it logs everyone out. Logout is front-channel only, so a Keycloak session ended elsewhere reaches the BFF cookie at the next refresh, at most five minutes later | `session.ts`, `refresh.ts` | R |
| B-4 | **Keycloak realm:** password policy has no breached-password check or history; no MFA required action; SMTP has no authentication or TLS fields; no production overlay; OpenTofu state is local | `infra/keycloak/base/main.tf` | R |
| B-5 | Authorization gaps listed by the [security review](../temp/authorization-security-review.md): S2 to S13 stay as planned in milestones 7 to 17, except S1 (fixed here) | review | R |

## Quality, supply chain and operations

| ID | Finding | Evidence | Sure |
|---|---|---|---|
| Q-1 | **No coverage measurement** (JaCoCo, Vitest coverage) and no formatter, linter or static analysis for Java | `pom.xml`, `vitest.config.ts` | R |
| Q-2 | **No CodeQL or other SAST, no dependency audit, no SBOM, no image signing or provenance.** Actions are pinned by tag, except Trivy | `ci.yml` | R |
| Q-3 | **No custom metrics.** Nothing counts failed or exhausted events, authorization denials, Keycloak latency, purge results or rate-limit rejections; OTLP log export is not wired (OQ-42) | grep: no `MeterRegistry` | R |
| Q-4 | **Test gaps:** no migration-upgrade test (only from empty), no event-payload contract test, no load or performance baseline, no two-tenant request test, no Keycloak slow-response test | explorer reports | R |
| Q-5 | **CI behaviour:** any label added to a PR re-runs the whole matrix; `cancel-in-progress` also cancels runs on `main`; no job timeouts on four jobs | `ci.yml` | R |
| Q-6 | **Repository files** missing before this round: root README, SECURITY, CODEOWNERS, PR template, `.editorconfig`, `.nvmrc`. A LICENSE is still missing ([OQ-48](../decisions/open-questions.md)) | `git ls-files` | V. **Fixed here** except LICENSE |
| Q-7 | **Compose is a development file:** fixed `container_name`, no resource limits, secrets in environment variables visible to `docker inspect`, floating `mysql:8.4` tag, MySQL root password on a command line in the health check | `compose.yaml` | R |

## Documentation

| ID | Finding | Evidence | Sure |
|---|---|---|---|
| P-1 | **39 of the 52 accepted or deprecated ADRs opened their banner with `planned` or `observed`, and 15 specs, guides and recipes carried a banner that the CI record contradicts** (for example "no CI run yet" for the recipes, whose `module-removal` rows passed in run 42959703043). The architecture overview still said "nothing in this picture except the backend skeleton and MySQL exists" | grep of `Implementation:` banners; `gh run view 42959703043` | V. **Corrected here** |
| P-2 | **ADRs carry implementation banners, which the documentation standards forbid** (section 7: ADRs "do not describe the current state of the code"). They go stale after every milestone, which is why P-1 exists | [documentation standards](../meta/documentation-standards.md) | V |
| P-3 | Stale rows in [implementation status](../product/roadmap/implementation-status.md) (input validation, "no generated API client", "83 Vitest tests", "not yet re-marked") | file | V |
| P-4 | There was no data inventory and no module catalogue. **Both are written here** | | V |
| P-5 | `temp/` holds nine working files, about 5,000 lines, committed; two are superseded by the permanent docs | `git ls-files` | V |

## What an enterprise does differently

The table keeps only what changes the design or the risk. "Where" names the proposal; every item is a proposal until the owner accepts it.

| ID | Recommendation | Closes | Severity | Effort | Where |
|---|---|---|---|---|---|
| R-01 | Require the caller to hold every permission of a role they assign | S-1 | High | S | **Done on `m-5-1`** |
| R-02 | Declare every module's allowed dependencies | C-3 | Medium | S | **Done on `m-5-1`** |
| R-03 | Let each consumer own the port it needs (a user lookup in `access`, actor names in `audit`, both implemented by `identity`); move the last-administrator rule to `identity`, which owns user state; make rate-limit buckets a registry that modules declare into; name `StoredFiles` and `FileCategory` as the shared storage SPI | C-1, C-2, E-1 | Medium | M | [ADR 0057](../decisions/0057-consumer-owned-ports-and-declared-module-dependencies.md) |
| R-04 | _Withdrawn: `access` handling `UserErased` would create a module cycle (see C-4)._ | | | | |
| R-05 | **Done on branch `m-5-1-tx`** (2026-11-25; ADR 0058 records what changed). No external call inside a database transaction: external handlers run outside it; the files purge deletes objects first, then rows in a short transaction | T-1, T-2 | High | M | [ADR 0058](../decisions/0058-no-external-call-inside-a-database-transaction.md) |
| R-06 | **Done on branch `m-5-1-tx`**, with a bounded retry and without a circuit breaker, as ADR 0058 decided. Timeouts, bounded retry and a circuit breaker for Keycloak; make the compensating delete durable | T-3 | Medium | M | ADR 0058 |
| R-07 | **Done on branch `m-5-1-data`** (2026-11-25; the unique email key was left out, see ADR 0059). Foreign keys inside a module; a database-enforced unique email; a versioned event envelope with a deserialization test; purge and measure failed events; erase files even when the module is off | D-1 to D-5, T-6 | Medium | M | [ADR 0059](../decisions/0059-data-integrity-in-the-database.md) |
| R-08 | ADRs point to implementation status instead of repeating it; specs carry one accurate line; generate module documentation from Modulith; check links in CI | P-1, P-2 | Medium | S | [ADR 0060](../decisions/0060-implementation-status-in-one-place.md) |
| R-09 | **Done on branch `m-5-1-ci`** (2026-11-25; the Java formatter was left out, see ADR 0061). Coverage reports then gates, CodeQL, dependency review, SBOM, actions pinned by SHA, `tofu validate` and `docker compose config` checks | Q-1, Q-2 | Medium | M | [ADR 0061](../decisions/0061-ci-quality-and-supply-chain-gates.md) |
| R-10 | Custom metrics and alerts for failed events, denials, Keycloak calls, purge counts and rate-limit rejections | Q-3 | Medium | M | [Milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md) |
| R-11 | Decide before multi-tenancy whether MySQL stays: row-level security in PostgreSQL would replace the hand-written tenant conditions and answer S7 | D-8, D-9 | Medium | L (decision S) | [ADR 0062](../decisions/0062-reassess-the-application-database-before-multi-tenancy.md) |
| R-12 | CSP with nonces, HSTS, `__Host-` cookie, session-key ring, back-channel logout, proxy timeout, body limit, header allow-list and client address; fail fast in production on localhost defaults | B-1 to B-3, E-3 | Medium | M | [ADR 0063](../decisions/0063-bff-and-edge-hardening.md) |
| R-13 | Split `identity/application` by concern; extract the shared attachment logic next to `StoredFiles` so a feature does not copy it | C-6, C-7 | Low | M | Milestone 5.1 |
| R-14 | Break the frontend `identity` and `access` import cycle; generate permission constants from the API; move shared shell parts out of `features/` | C-8 | Low | M | Milestone 5.1 |
| R-15 | Test the upgrade path (apply the migrations of the previous release, then the new ones), event payload compatibility and a baseline of requests per second | Q-4, T-4 | Medium | M | Milestone 5.1 |
| R-16 | Repository files: README, SECURITY, CODEOWNERS, PR template, `.editorconfig`, `.nvmrc`; choose a licence | Q-6 | Low | S | **Done on `m-5-1`** except the licence (OQ-48) |
| R-17 | Decide the order of work with the deployment question in view. Harden first (5.1), then the contract (7). Whether the first deployable release (6) comes before the grant model (8) or after it is OQ-85; the lean here is **before**, so that backup, TLS, production configuration and the end-to-end tests meet reality before the stored model grows, and eleven authorization phases do not queue ahead of anything that can be deployed. The earlier review leaned the other way, so the first tag carries the new contract; with no forks and no users that argument is weak, but it is the owner's call | roadmap | High | n/a | [OQ-85, OQ-89](../decisions/open-questions.md) |
| R-18 | Keep the engine decision of ADR 0056 genuinely open: in-house on MySQL carries the cost of every later phase | roadmap | Medium | n/a | ADR 0056 |

## Proposed order

1. **This round (done):** R-01, R-02, R-16, the data inventory, the module catalogue and this assessment.
2. **Milestone 5.1:** R-05 to R-10, R-13 to R-15. Each is small, and none changes behaviour users can see.
3. **Owner decisions:** accept or change ADRs 0057 to 0063; OQ-85 and OQ-89 (order of work); OQ-48 (licence).
4. **Then** milestone 7, then milestone 6 or 8 in the order the owner chooses (OQ-85), then the rest of the authorization phases.

## What this round fixed

| What | Evidence |
|---|---|
| S-1 / R-01: role assignment now requires the caller to hold the permissions of each role they add | `AccessApiIT.assigningCannotEscalate` and `UserAdministrationIT.invitingCannotEscalate` pass; with the check removed they fail with 200 and 201 (verified, CI run 45354022559, 2026-11-24) |
| C-3 / R-02: `allowedDependencies` for `access`, `audit`, `files`, `identity`, `notes` | `ModularityTest` passes; with `notes` denied access to `identity` it fails with the named violation (verified, CI run 45354022559, 2026-11-24) |
| Q-6 / R-16: README, SECURITY, CODEOWNERS, PR template, `.editorconfig`, `.nvmrc` | Files exist |

## Limits of this assessment

Nothing was run except the tests named above. A full backend run on branch `m-5-1` passed 135 fast and 271 integration tests with no change to `openapi.json` (local, 2026-11-24, recorded in [implementation status](../product/roadmap/implementation-status.md); CI run 45354022559). The frontend was read, not run. Findings tagged **R** or **I** should be confirmed by a test before a design change relies on them.

## Related

[Module catalogue](module-catalogue.md), [data inventory](data/data-inventory.md), [layers and boundaries](layers-and-boundaries.md), [architecture enforcement](architecture-enforcement.md), [authorization security review](../temp/authorization-security-review.md), [open questions](../decisions/open-questions.md).
