# Implementation Status

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** this document is the reference for implementation status. The rows were first written from a snapshot of 2026-10-27 and updated on 2026-11-08 after milestone 0 was implemented.

The one place that compares what is intended with what exists. Values follow the [vocabulary](../../meta/living-documentation.md): planned, observed, verified. The [technology catalogue](../../technology/README.md) lists what is selected and links here for status.

**Milestone 0 is verified.** Its 13 tests and the secret scan passed in CI on Eclipse Temurin 25 on pull request #2 (run 42477195261, 2026-11-08) and again on `main` after the merge (run 42477240084), as the [verification standard](../../engineering/testing/verification-standard.md) requires. Milestones 1 and 2 are built, and their CI jobs (backend, frontend, image scan, end-to-end sign-in, secret scan) passed on pull request #4 (run 42605511477, 2026-11-09). Milestone 3 is built and its CI jobs (backend, frontend, image scan, end-to-end, secret scan) passed on pull request #5 (run 42780048416, 2026-11-10). Rows that said "no CI run yet" were re-marked on 2026-11-24 against the CI record (jobs of runs 42589726681, 42605511477 and 42959703043); a row that still says so has no run to point to. Milestone 4 is built and its CI jobs (backend with 103 fast and 238 integration tests, frontend with 168 unit tests, image scan, 22 end-to-end tests, secret scan) passed on pull request #6 (run 42873216224, 2026-11-10). Milestone 5 is built and its CI jobs (backend, frontend, image build and scan, secret scan, and the four rows of the `module-removal` job) passed on pull request #7 (run 42959703043, 2026-11-11) and again on `main` after the merge (run 42981706613). Its 31 end-to-end tests were skipped in both of those runs and passed in CI for the first time on 2026-11-24 (run 45354022559, pull request #10), after the three defects they exposed were fixed; OQ-86 is closed. Milestone 6 is unbuilt. Milestone 5.1 is built in part (a security fix, declared module dependencies, repository files and an [architecture assessment](../../architecture/architecture-assessment.md); CI run 45354022559, 2026-11-24). Milestone 7 (CI run 45354823982), milestone 8 (CI run 45384428905) and the Open Policy Agent guardrail (CI run 45385703846) are built and passed every CI job, all on 2026-11-24 (tables at the end). Authorization beyond milestone 3 is planned in the [authorization roadmap](authorization-roadmap.md); how far the code is from each requirement is in [traceability](../requirements/authorization/traceability.md). The container image scan passed for both images (run 42959703043, 2026-11-11), which finishes OQ-44.

## Existing capabilities

| Capability | Intended (decision) | Observed in repository | Level |
|---|---|---|---|
| Spring Boot backend application | [ADR 0002](../../decisions/0002-modular-monolith.md) | `backend/` with `BackendApplication` (Boot 4.1.1, Maven wrapper) | observed |
| Java baseline | [ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md) (supersedes ADR 0019) | `pom.xml` sets `java.version` 25; CI uses Temurin 25. Local build and tests passed on Oracle JDK 25.0.5 (2026-11-08) | verified (CI run 42477195261, 2026-11-08) |
| Package/module structure | [ADR 0002](../../decisions/0002-modular-monolith.md), [ADR 0003](../../decisions/0003-hexagonal-architecture-where-boundaries-matter.md), [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) | Six module packages under `com.kibootia.backend` with code (`platform`, `identity`, `access`, `audit`, `files`, `notes`); `files` and `notes` are removable | observed |
| ArchUnit rules and Spring Modulith verification | [ADR 0013](../../decisions/0013-archunit-architecture-enforcement.md), [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) | Spring Modulith 2.1.2 and ArchUnit 1.5.1; `ModularityTest` and `ArchitectureTest` (rules 1, 4, 6, 8) pass locally; rules 5 and 7 not implemented | verified (CI run 42477195261, 2026-11-08) |
| Spring Security resource server | [ADR 0007](../../decisions/0007-spring-security-resource-server.md) | `platform/security`: stateless, deny-by-default chain, no CORS, 401 and 403 as Problem Details, a decoder that checks the public issuer and fetches keys from a separate address. `ResourceServerIT` (14 tests) passed locally on 2026-11-09 | verified (`ResourceServerIT`, 14 tests, in the backend CI job; CI run 42605511477, 2026-11-09) |
| Keycloak / OIDC | [ADR 0006](../../decisions/0006-keycloak-oidc-identity.md), [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md), [ADR 0045](../../decisions/0045-keycloak-operations.md) | Keycloak 26.8.2 on PostgreSQL 17.11 in `compose.yaml`; realm `kibootia` applied by OpenTofu 1.13.9 with provider 5.10.0 (confidential PKCE client, API audience mapper, least-privilege admin service account, demo user). Sign-in through Next.js passed a Playwright test locally on 2026-11-09 | verified (the CI end-to-end sign-in, run 42589726681, 2026-11-09, and every later run that executed it) |
| Audience validation, deny-by-default | [ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md) | `AudienceValidator` and the deny-by-default chain; tests for missing, wrong and absent audience and for unmapped paths | verified (`ResourceServerIT`; CI run 42605511477, 2026-11-09) |
| MySQL | [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md) | MySQL 8.4 in `compose.yaml` (host port 3307); driver in pom; datasource properties set | observed |
| Spring Data JPA / Hibernate | [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md) | `spring-boot-starter-data-jpa` in the pom; `ddl-auto=validate` takes effect and validates the event publication table (passing locally) | verified (CI run 42477195261, 2026-11-08) |
| Flyway migrations | [ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md) | `db/migration/platform/V202611091200__platform_event_publication.sql`; migrate-from-empty is exercised by `BackendApplicationIT` | verified (CI run 42477195261, 2026-11-08) |
| Input validation | [API conventions](../../architecture/api/conventions.md) | Validation starter present; no usage | verified (Bean Validation on request types, mapped to 400 with field errors; `ApiConventionsIT`; CI run 42605511477, 2026-11-09) |
| Secret delivery from environment and files | [ADR 0032](../../decisions/0032-secrets-from-environment-and-files.md), [ADR 0016](../../decisions/0016-platform-injected-secrets.md) | Backend reads `MYSQL_PASSWORD` from the environment or the local `.env`; no secret in `application.properties` | observed |
| Externalised configuration | [configuration](../../operations/configuration/application-configuration.md) | No secret in version control; `.env.example` and `pnpm bootstrap` exist; a missing required setting stops startup with a named message (`RequiredSettingsTest`). The credentials that were committed to git history were rotated by the owner (reported 2026-11-08) | verified (CI run 42477195261, 2026-11-08); rotation reported by the owner |
| Health, logging, correlation ID | [observability baseline](../../operations/monitoring/observability-baseline.md) | Actuator with only `health` (liveness, readiness on MySQL) on port 8090, loopback only; ECS JSON console logs with trace and span IDs (tested); traces and metrics over OTLP. OTLP log export is not wired (OQ-42). No correlation ID is returned to callers except in Problem Details `traceId` | verified for health on the management port and for ECS logs with trace and span IDs (`ResourceServerIT`, `TraceLoggingIT`; CI run 42605511477); OTLP log export is not wired (OQ-42) |
| Testcontainers persistence tests | [ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md) | Testcontainers MySQL 8.4 with `@ServiceConnection`; `BackendApplicationIT` and `EventPublicationRegistryIT` pass locally | verified (CI run 42477195261, 2026-11-08) |
| CI | [ADR 0018](../../decisions/0018-ci-as-verification-standard.md) | `.github/workflows/ci.yml` (backend build and test, gitleaks secret scan) and `renovate.json` added; green on pull request #2 and on `main`. Renovate was installed by the owner on 2026-11-08 and has not yet opened a pull request. Milestone 1 added frontend, image build and scan, and end-to-end jobs; they have not run | verified: backend, frontend, image build and Trivy scan of both images, the four `module-removal` rows and the secret scan passed in run 42959703043 (2026-11-11); the end-to-end job ran on pull request #4 (run 42589726681) was skipped in the milestone 5 runs, and passed with all 31 tests in run 45354022559 (2026-11-24) |
| Next.js frontend | [ADR 0021](../../decisions/0021-frontend-beside-backend-in-one-repository.md), [ADR 0038](../../decisions/0038-frontend-stack.md), [ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md) | `frontend/`: Next.js 16 BFF with sign-in, session, refresh, sign-out, the `/api/*` proxy, `/healthz`, a `/me` page; 83 Vitest tests, lint, type check and build passed locally on 2026-11-09. No generated API client, shadcn/ui or TanStack Query yet | verified for lint, type check, unit tests and build (196 unit tests; CI run 42959703043, 2026-11-11); the sign-in story passed in the end-to-end job (run 42589726681). The milestone 5 browser stories passed in run 45354022559 (2026-11-24) |
| Container image / deployment | [container deployment](../../operations/deployment/container-deployment.md), [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md), [ADR 0047](../../decisions/0047-health-exposure-and-container-images.md) | `backend/Dockerfile` and `frontend/Dockerfile` (multi-stage, non-root); the `apps` Compose profile ran both with a working sign-in. No deployment recipe yet | verified for building and scanning both images (CI run 42959703043, 2026-11-11); the deployment itself (reverse proxy, TLS, migration step, backups) is milestone 6 and not built |
| Automated tests | [testing strategy](../../engineering/testing/strategy.md) | 13 tests (context and migration, event registry, Modulith, ArchUnit, required settings); all passed in one local run on 2026-11-08 | verified in CI at milestone 5 (run 42959703043); on branch `m-5-1` a full local run passed 135 fast and 271 integration backend tests (2026-11-24; CI run 45354022559) |

## Capabilities added by the decisions of 2026-10-28

The Level column was added on 2026-11-09.

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Next.js backend-for-frontend sign-in and encrypted cookie session | [ADR 0026](../../decisions/0026-nextjs-bff-session.md) | [1](milestone-1-walking-skeleton.md) | verified (the CI end-to-end sign-in, run 42589726681, 2026-11-09; session unit tests in the frontend job) |
| Local Keycloak with an imported realm, hardened | [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md) | [1](milestone-1-walking-skeleton.md) | verified (the CI end-to-end job brings it up and signs in, run 42589726681, 2026-11-09); the realm is applied by OpenTofu, not imported |
| OTLP telemetry and the local Grafana LGTM container | [ADR 0030](../../decisions/0030-otlp-telemetry-and-local-lgtm.md) | [1](milestone-1-walking-skeleton.md) | partly observed (traces yes, log export no: OQ-42) |
| UUIDv7 identifiers, audit columns, optimistic locking | [ADR 0033](../../decisions/0033-uuidv7-identifiers.md) | [2](milestone-2-platform-conventions.md) | verified (CI run 42605511477, 2026-11-09) |
| Tenant context and isolation | [ADR 0028](../../decisions/0028-tenant-ready-schema.md) | [2](milestone-2-platform-conventions.md) | verified (CI run 42605511477, 2026-11-09) |
| Soft delete, restore and retention purge | [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md) | [2](milestone-2-platform-conventions.md) | verified (CI run 42605511477, 2026-11-09); the purge of stored files, built in milestone 4, is verified too (CI run 42873216224, 2026-11-10) |
| Outbox and lock-protected jobs | [ADR 0031](../../decisions/0031-transactional-outbox-and-locked-jobs.md) | [2](milestone-2-platform-conventions.md) | verified (CI run 42605511477, 2026-11-09) |
| Generated OpenAPI description and client | [ADR 0038](../../decisions/0038-frontend-stack.md) | [2](milestone-2-platform-conventions.md) | verified (CI run 42605511477, 2026-11-09) |
| User profile, roles, permissions and decision point | [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md) | [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42780048416, 2026-11-10) |
| User and role administration UI, invitations, audit log viewer, rate limiter | [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md) | [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42780048416, 2026-11-10); the uploads limit applied in milestone 4 is verified too (CI run 42873216224, 2026-11-10) |
| Object storage module and avatar flow | [ADR 0029](../../decisions/0029-s3-object-storage-contract.md), [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md) | [4](milestone-4-files.md) | verified (CI run 42873216224, 2026-11-10) |
| Example feature (Notes) with a picture and screens, removal script and recipes, module-removal CI job | [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | verified (CI run 42959703043, 2026-11-11; the end-to-end tests in run 45354022559, 2026-11-24) |
| Reference deployment, production guards, first recipes, release process | [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md), [ADR 0023](../../decisions/0023-template-fork-adoption-model.md) | [6](milestone-6-production-readiness.md) | planned |
| Threat model | [threat model](../../operations/security/threat-model.md) | document written; entries added for milestone 3 | observed (documentation; mitigations as in their rows) |

## Capabilities added by the decisions of 2026-11-08

The Level column was added on 2026-11-09.

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Spring Modulith verification and the event publication registry as the outbox | [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) | [0](milestone-0-hygiene-and-guardrails.md), [2](milestone-2-platform-conventions.md) | verified (CI run 42477195261, 2026-11-08) |
| Setup scripts (`pnpm bootstrap`, `infra:up`, `dev`, `reset`) and the `apps` Compose profile | [ADR 0040](../../decisions/0040-local-development-topology-and-setup.md) | [0](milestone-0-hygiene-and-guardrails.md), [1](milestone-1-walking-skeleton.md) | verified for `pnpm bootstrap` and the `apps` profile (the CI end-to-end job runs them, run 42589726681, 2026-11-09); `infra:up`, `dev` and `reset` are observed (local run) |
| Invitations, self-registration, default `user` role, email verification in the decision point | [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md) | [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42780048416, 2026-11-10) |
| Generic `/api` proxy and CSRF rule in the Next.js server | [ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md) | [1](milestone-1-walking-skeleton.md) | verified (the proxy unit tests in the frontend job, and the CSRF refusal in the sign-in story; CI run 42589726681, 2026-11-09) |
| Problem Details errors, `/api/v1`, committed `openapi.json` with drift check | [ADR 0043](../../decisions/0043-http-api-conventions.md) | [2](milestone-2-platform-conventions.md) | verified (CI run 42605511477, 2026-11-09) |
| `scopeFor`, `@RequiresPermission`, `@NoAuthorization` and its ArchUnit rule | [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md) | [2](milestone-2-platform-conventions.md), [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42605511477, 2026-11-09) for the mechanism; the stored permissions it reads are verified (CI run 42780048416, 2026-11-10) |
| Keycloak on PostgreSQL, realm applied by OpenTofu, `auth.<domain>` exposure | [ADR 0045](../../decisions/0045-keycloak-operations.md) | [1](milestone-1-walking-skeleton.md), [6](milestone-6-production-readiness.md) | verified (OQ-46: CI end-to-end, run 42589726681, 2026-11-09); `auth.<domain>` exposure is milestone 6 |
| Audit module with user-ID-only entries and a 365-day default retention | [ADR 0046](../../decisions/0046-audit-log-design.md) | [2](milestone-2-platform-conventions.md), [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42605511477, 2026-11-09) for writing and purging; the viewer is verified (CI run 42780048416, 2026-11-10) |
| Health on the management port, multi-stage Dockerfiles | [ADR 0047](../../decisions/0047-health-exposure-and-container-images.md) | [1](milestone-1-walking-skeleton.md) | verified (`ResourceServerIT` for the management port; both images built and scanned in CI run 42959703043, 2026-11-11) |

## Capabilities added by the decisions of 2026-11-10

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Keycloak Admin API port and adapter with a least-privilege service account | [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md) | [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42780048416, 2026-11-10); the adapter test runs against a real Keycloak |
| Request gate: profile on first use, refresh from the token, disabled and deleted users refused | [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md) | [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42780048416, 2026-11-10) |
| Stored roles, protected administrator role, last-administrator safeguard, bootstrap administrator | [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md) | [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42780048416, 2026-11-10) |
| Per-user in-app rate limiter (Bucket4j) | [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md) | [3](milestone-3-identity-and-access-administration.md), [4](milestone-4-files.md) | verified (CI run 42780048416, 2026-11-10); applied to uploads in milestone 4 (`AvatarRateLimitIT`; verified, CI run 42873216224, 2026-11-10) |
| Admin, profile and audit screens; forced session refresh; end-to-end stories | [ADR 0038](../../decisions/0038-frontend-stack.md), [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md) | [3](milestone-3-identity-and-access-administration.md) | verified (CI run 42780048416, 2026-11-10) |
| S3 port and adapter on the AWS SDK, RustFS 1.0.4 in the `files` Compose profile, the gate of OQ-40 | [ADR 0029](../../decisions/0029-s3-object-storage-contract.md), [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md) | [4](milestone-4-files.md) | verified (CI run 42873216224, 2026-11-10: 12 contract tests, including presigned PUT and GET) |
| Upload pipeline: size limit while reading, type from the bytes, inspection hook, re-encoding with EXIF orientation applied and all metadata dropped, a 40-megapixel limit | [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md) | [4](milestone-4-files.md) | verified (`ImageProcessorTest`, `AvatarApiIT`; CI run 42873216224, 2026-11-10) |
| Avatar upload, replace, view and delete, with permission checked on every view, ETag revalidation, tenant isolation and a verified address for changes | [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md), [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md) | [4](milestone-4-files.md) | verified (`AvatarApiIT`, 168 frontend unit tests and the avatar end-to-end story; CI run 42873216224, 2026-11-10) |
| Retention purge of files, daily reconciliation of objects and rows, removal of a user's files on `UserErased` | [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md), [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md) | [4](milestone-4-files.md) | verified (`FileJobsIT`; CI run 42873216224, 2026-11-10) |
| The files module can be switched off with `kibootia.files.enabled`; ArchUnit rule 5 for it | [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md) | [4](milestone-4-files.md) | verified (`FilesDisabledIT`, `ArchitectureTest`; CI run 42873216224, 2026-11-10); the all-modules job is milestone 5 (below) |

## Capabilities added by the decisions of 2026-11-11

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Fit renditions: a picture kept whole and in proportion, never enlarged, beside the square avatar | [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | verified (`ImageProcessorTest`; CI run 42959703043, 2026-11-11) |
| A note's picture: attach, replace, view, remove; permission follows the note; file owned by the note's owner; no change to the note's version; upload allowance spent after the permission check | [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | verified (`NoteAttachmentApiIT`, `NoteAttachmentRateLimitIT`; CI run 42959703043, 2026-11-11) |
| A deleted note hides its picture and a restore brings it back; the purge discards the file through a durable event (`NotePurged`) | [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md), [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md) | [5](milestone-5-example-feature.md) | verified (`NoteAttachmentApiIT`; CI run 42959703043, 2026-11-11) |
| The deleted-notes list (`?deleted=true`) for someone who may restore notes | [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | verified (`NotesApiIT`; CI run 42959703043, 2026-11-11) |
| `filesEnabled` in `/me` | [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | verified (`FilesDisabledIT`, `ModuleStartupIT`; CI run 42959703043, 2026-11-11) |
| Notes screens (list, editor, picture), the feature module registry and the shared image check | [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | verified (196 unit tests, lint, type check, build; CI run 42959703043, 2026-11-11). The browser story passed in CI run 45354022559 (2026-11-24) |
| Shared tests no longer name the notes module (`WidgetFixtures`); architecture rule 5 for every removable module; rules shown to fire | [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | verified (`ArchitectureTest`, `ArchitectureRulesFireTest`, `ModularityTest`; CI run 42959703043, 2026-11-11) |
| `scripts/remove-module.mjs`, the removal recipes and the `module-removal` CI job (notes removed, files off, telemetry off, all three) | [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | verified: all four rows of the `module-removal` job passed (CI run 42959703043, 2026-11-11) |
| The guide to copy the example feature | [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md) | [5](milestone-5-example-feature.md) | observed (documentation) |

## Capabilities added on branch `m-5-1` (2026-11-24)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Role assignment requires the caller to hold the permissions of each role they add (closes S1 of the [security review](../../temp/authorization-security-review.md)) | [assessment](../../architecture/architecture-assessment.md) S-1 | [5.1](milestone-5-1-architecture-hardening.md) | verified (`AccessApiIT.assigningCannotEscalate`, `UserAdministrationIT.invitingCannotEscalate`; both fail with 200 and 201 when the check is removed; full backend run 135 fast and 271 integration tests, CI run 45354022559, 2026-11-24) |
| `allowedDependencies` declared for `access`, `audit`, `files`, `identity` and `notes`; architecture rule 12 | [ADR 0057](../../decisions/0057-consumer-owned-ports-and-declared-module-dependencies.md) item 1 (draft) | 5.1 | verified (`ModularityTest`; an undeclared edge fails it; CI run 45354022559, 2026-11-24) |
| Root README, SECURITY, CODEOWNERS, pull request template, `.editorconfig`, `.nvmrc` | assessment R-16 | 5.1 | observed (files exist; the licence waits for OQ-48) |
| [Module catalogue](../../architecture/module-catalogue.md), [data inventory](../../architecture/data/data-inventory.md), [architecture assessment](../../architecture/architecture-assessment.md) | none | 5.1 | observed (documentation, read from the code; not machine-checked) |
| Implementation banners of ADRs 0001 to 0052 replaced by a fixed pointer to this document | [ADR 0060](../../decisions/0060-implementation-status-in-one-place.md) | 5.1 | observed (documentation) |

## Capabilities added on branch `m-7` (2026-11-24)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Decision contract version 2: typed principal, resource reference, decision with a stable reason code, access filter for lists | [ADR 0055](../../decisions/0055-authorization-decision-contract.md), [ADR 0064](../../decisions/0064-milestone-7-implementation-decisions.md) | [7](milestone-7-authorization-contract.md) | verified (`DefaultDecisionPointTest`, `AuthorizationIT` and every authorization integration suite, local run 2026-11-24; CI run 45354823982) |
| Module rules that can only narrow; every applicable rule consulted; a failing rule refuses (closes S3) | ADR 0064 | 7 | verified (`DefaultDecisionPointTest`, `AuthorizationIT`; CI run 45354823982) |
| Decision records for refusals and for allowed uses of sensitive permissions, and the decision metric | ADR 0064 | 7 | verified (`AuthorizationIT` reads the JSON log line and the counter; CI run 45354823982) |
| One rule for invisible records: 403 without any grant, 404 alike for missing and out of reach (resolves OQ-87) | ADR 0064 | 7 | verified (`NotesApiIT`, `NoteAttachmentApiIT`, `AuthorizationIT`; CI run 45354823982) |
| The request gate is required at startup (closes S11) | ADR 0064 | 7 | verified (every integration test starts with it; CI run 45354823982) |
| Administration sections guarded one by one; an ended session sends the user to sign in (closes S13) | ADR 0064 | 7 | verified (`admin-frame.test.tsx`, `session-expiry.test.ts`, 204 unit tests; CI run 45354823982) |

## Capabilities added on branch `m-8` (2026-11-24)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Declared actions (on the collection or on a record) and resource types with relations; startup refuses undeclared types, unavailable reaches and `@RequiresPermission` on an action on a record | [ADR 0065](../../decisions/0065-milestone-8-implementation-decisions.md) | [8](milestone-8-grant-model.md) | verified (`AccessConfigurationTest`, `DefaultDecisionPointTest`; CI run 45384428905, 2026-11-24; local run first) |
| Roles as grants with a reach (`all`, `relation:owner`); the `-any` permissions removed; the administrator an explicit role kept complete at startup | ADR 0065 | 8 | verified (`GrantsApiIT`, `BuiltInRolesIT`, `AccessApiIT`, `NotesApiIT`; CI run 45384428905, 2026-11-24; local run first) |
| Conversion of stored version-1 permissions into grants at startup, with every decision and list filter equal before and after | ADR 0065 | 8 | verified (`LegacyGrantEquivalenceTest`: 128 sets of stored names, every action and kind of record; `BuiltInRolesIT`; CI run 45384428905, 2026-11-24; local run first) |
| Deny grants behind `kibootia.access.deny-grants-enabled`, off by default; a deny decides over every allow | ADR 0065 | 8 | verified (`DenyGrantsIT`, `DefaultDecisionPointTest`; CI run 45384428905, 2026-11-24; local run first) |
| "Only what you hold" covers reach, for role editing and role assignment | ADR 0065 | 8 | verified (`GrantsApiIT`; CI run 45384428905, 2026-11-24; local run first) |
| `/me` reports the reach of each held action; the role editor chooses a reach per grant and keeps deny grants | ADR 0065 | 8 | verified (`GrantsApiIT`, `role-editor.test.tsx`; CI run 45384428905, 2026-11-24; local run first) |
| Architecture rule 14: hand-written SQL names the tenant (closes S7 for the code as it is) | ADR 0065 | 8 | verified (`TenantSqlRulesTest`, with a negative control; CI run 45384428905, 2026-11-24; local run first) |
| One decision is one SQL statement, measured: on the collection, on a record, a list filter and a refusal (the baseline of milestone 7, criterion 4) | [ADR 0065](../../decisions/0065-milestone-8-implementation-decisions.md) | 8 | verified (`DecisionQueryCountIT`; CI run 45384428905, 2026-11-24; local run first) |

## Capabilities added on branch `m-policy` (2026-11-24)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Module rules may refuse an action on the collection; an objection to a list's action refuses the list | [ADR 0066](../../decisions/0066-opa-as-a-narrowing-guardrail.md) | none | verified (`DefaultDecisionPointTest`; CI run 45385703846, 2026-11-24; local run first) |
| Optional `policy` module: an Open Policy Agent guardrail asked about the configured actions only, narrowing only, failing closed | ADR 0066 | none | verified (`OpaGuardrailIT` against OPA 1.22.2, `OpaUnreachableIT`, `PolicyConfigurationTest`; CI run 45385703846, 2026-11-24; local run first) |
| Shipped guardrail policy with its own tests; OPA in the Compose profile `policy` | ADR 0066 | none | verified (`opa test infra/opa`, 4 tests; CI run 45385703846, 2026-11-24; local run first) |
| Every read of the audit log leaves a decision record naming the reader (finding S10, in part) | [ADR 0064](../../decisions/0064-milestone-7-implementation-decisions.md) (sensitive permissions) | none | verified (`AuditViewerIT.readingIsRecorded`, with a negative control; CI run 45385703846, 2026-11-24; local run first) |

## Capabilities added on branch `m-5-1-ci` (2026-11-25)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Coverage reports: JaCoCo for the backend (instructions and branches in the run summary) and Vitest for the frontend (68% of statements on 2026-11-25), kept as artifacts, no threshold | [ADR 0061](../../decisions/0061-ci-quality-and-supply-chain-gates.md) item 1 | 5.1 | observed (local run 2026-11-25: `DefaultDecisionPointTest` covers 435 of 444 instructions of its class; frontend 207 tests; CI run pending) |
| CodeQL for Java and TypeScript; dependency review on pull requests; `pnpm audit --prod` | ADR 0061 items 2 and 3 | 5.1 | observed (workflows written and linted with actionlint; CI run pending) |
| A CycloneDX bill of materials per image; every action pinned by upstream commit SHA (the Trivy pin that was not an upstream commit is replaced); job timeouts; no cancelling on `main`; a label other than `e2e` runs nothing | ADR 0061 items 4, 5 and 6 | 5.1 | observed (actionlint; CI run pending) |
| Infrastructure checks: `tofu fmt -check` and `validate`, `docker compose config` with every profile, hadolint | ADR 0061 item 7 | 5.1 | observed (each run locally on 2026-11-25; one formatting fix in `infra/keycloak/base/main.tf`; CI run pending) |
| Documentation check: relative links resolve and the decision index agrees with each ADR (`scripts/check-docs.mjs`, shown to fire on a broken link and on a wrong status) | [ADR 0060](../../decisions/0060-implementation-status-in-one-place.md), ADR 0061 | 5.1 | observed (local run 2026-11-25: 203 files, 66 decision records; CI run pending) |
| Spring Java Format | ADR 0061 item 8 | 5.1 | not adopted: 285 files fail it because the code is written to about 120 columns (recorded in ADR 0061) |

## Capabilities added on branch `m-5-1-data` (2026-11-25)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Foreign keys from `role_permission` and `role_assignment` to `role`, with a migration that removes rows of roles that are gone | [ADR 0059](../../decisions/0059-data-integrity-in-the-database.md) item 1 | 5.1 | observed (`RoleForeignKeysIT`, which runs the migration script against orphan rows; branch `m-5-1-data`; CI run pending) |
| A schema version on every event, a golden file per event type that the current code must read, and the rule for changing an event | ADR 0059 item 3 | 5.1 | observed (`EventSchemaCompatibilityIT`, `DurableEventsIT`; CI run pending) |
| `event_publication.serialized_event` is `TEXT`; a 6,000-character event is stored and delivered | ADR 0059 item 4 | 5.1 | observed (`DurableEventsIT`, and Hibernate validation at every startup; CI run pending) |
| Failed events whose retries are exhausted are removed after 90 days, audited; a gauge counts failed events | ADR 0059 item 5 | 5.1 | observed (`FailedEventRetentionIT`, `DurableEventsIT`; CI run pending) |
| A warning and a health detail when files are switched off while stored files remain | ADR 0059 item 6 | 5.1 | observed (`FilesDisabledIT`; CI run pending) |
| A unique email key on `user_profile` | ADR 0059 item 2 | 5.1 | not built, with the reason recorded in the ADR: Keycloak already refuses a second account with an address, and a unique key on the refreshed copy would fail sign-ins |

## Capabilities added on branch `m-5-1-edge` (2026-11-25)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Content-Security-Policy with a nonce per page (report-only by default, `CSP_MODE`), HSTS over https and Cross-Origin-Opener-Policy, on every page | [ADR 0063](../../decisions/0063-bff-and-edge-hardening.md) item 1 | 5.1 | observed (`security-headers.test.ts`; branch `m-5-1-edge`; CI run pending). Also seen on a local production build with `curl` on 2026-11-25: the policy and the opener policy on pages, none on `/healthz`, and the header's nonce equal to the nonce on the page's scripts (15 of its 16 script tags carry one). No browser has loaded the page, and no end-to-end test reads the headers, so what a browser would report is not known |
| `__Host-` cookie names over https; a session is read only from the cookie of its own mode | ADR 0063 item 2 | 5.1 | observed (`session-keys.test.ts`; CI run pending) |
| A timeout (504) and a size limit (413) on the `/api` proxy; an allow-list of request headers | ADR 0063 items 3 and 4 | 5.1 | observed (`proxy-hardening.test.ts`; CI run pending) |
| The client's address passed on only from trusted proxies (`TRUSTED_PROXY_HOPS`) | ADR 0063 item 5 | 5.1 | observed in the frontend (`proxy-hardening.test.ts`); the backend does not read it yet (recorded in the ADR) |
| A session key ring: the first key seals, every key opens, a key identifier in the header; existing sessions stay readable | ADR 0063 item 6 | 5.1 | observed (`session-keys.test.ts`, including a token sealed as the old code did; CI run pending) |
| The server refuses to start in production without its addresses and secrets | ADR 0063 item 8 | 5.1 | observed (`config.test.ts`; CI run pending) |
| Back-channel logout | ADR 0063 item 7 | 6 | planned: it needs a server-side session store |

## Capabilities added on branch `m-5-1-tx` (2026-11-25)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| `@DurableEventListener(transactional = false)`: a handler that calls another system holds no database connection while it works; the mark is read and written in short transactions; `UserEvents` and `FileEvents` use it | [ADR 0058](../../decisions/0058-no-external-call-inside-a-database-transaction.md) item 2 | 5.1 | observed (`HandlerTransactionIT` counts the pool's connections; branch `m-5-1-tx`; CI run pending) |
| A purge can run without the job's transaction (`RetentionPurgeable.ownsTransactions`); the purge of deleted files deletes objects with none open | ADR 0058 item 3 | 5.1 | observed (`FileJobsIT`, and every integration test through the fakes' record; CI run pending) |
| Bounded retry of the Keycloak calls that are safe to repeat (find, enable or disable, end sessions, delete) with Spring Framework 7's retry support | ADR 0058 item 4 | 5.1 | observed (`KeycloakAdminAdapterRetryTest`, 7 tests against a local HTTP server; CI run pending) |
| A failed invitation whose account could not be taken back is cleaned up later, as a durable event | ADR 0058 item 5 | 5.1 | observed (`UserAdministrationIT`; CI run pending) |
| Architecture rule 13, in two halves, shown to fire; every integration test fails if Keycloak or storage was called while a transaction was open | ADR 0058 item 1 | 5.1 | observed (`ExternalCallRulesTest`, `ArchitectureTest`, `AdminApiTest`; CI run pending) |
| A reconciliation job that removes Keycloak accounts that have no profile | ADR 0058 item 5 | 5.1 | not built: a self-registered person has no profile until they first sign in, so such a job would delete real people (recorded in the ADR) |

## Capabilities added on branch `feat/admin-gaps` (2026-11-25)

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| An administration screen for failed events: which event failed in which handler, how often, whether it needs a person, and a retry button for `audit.failed-events.retry` | [ADR 0031](../../decisions/0031-transactional-outbox-and-locked-jobs.md) | none | observed (`failed-events-screen.test.tsx`, 8 tests; the Playwright admin story visits it; CI run pending) |
| Deny grants can be added and removed in the role editor while deny grants are on, for what the editor holds and for every record | [ADR 0065](../../decisions/0065-milestone-8-implementation-decisions.md) | none | observed (`role-editor.test.tsx`, 7 new tests; CI run pending). Not covered by a Playwright story |
| Actions only the system performs (the purges): not offered, not grantable, not held by the administrator role; the role and user purges have actions of their own | [ADR 0067](../../decisions/0067-actions-only-the-system-performs.md) | none | observed (`SystemOnlyPermissionTest`, `GrantsApiIT`; full local backend run 171 fast and 313 integration tests, one new test fixed and re-run; CI run pending) |
| The policy module is removable in CI (a row of the `module-removal` job; the "all" row removes both removable modules), and rule 8 names it | [ADR 0066](../../decisions/0066-opa-as-a-narrowing-guardrail.md) | none | observed (removal and build run locally on a copy; CI run pending) |
| The policy that ships in `infra/opa` is tested against a real OPA and the real guardrail | ADR 0066 | none | observed (`ShippedGuardrailPolicyIT`, 4 tests, local run; CI run pending) |
| Unit tests for the pager, the error alert and the roles and users lists | none | none | observed (`shared-components.test.tsx`, `roles-and-users-screens.test.tsx`; CI run pending) |

## Deviations the owner has called out

- Reference feature: a backend-only `notes` module ([ADR 0049](../../decisions/0049-milestone-2-implementation-decisions.md)); completed in milestone 5 with a picture, screens, a copy guide and removal recipes.
- `spring-boot-admin-starter-server` was removed from `pom.xml` on 2026-11-08.
- Java 17 in the pom versus the decided Java 25: resolved on 2026-11-08 ([ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md)).
- Secrets handling meets the target convention in the repository, and the owner rotated the credentials that were committed to git history.
