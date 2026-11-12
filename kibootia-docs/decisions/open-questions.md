# Open Questions

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (list).

Live list of unresolved decisions, deviations observed in the repository and checks that must pass before a decision is final. Remove an entry (and note it in the relevant document or ADR) when it is resolved. All entries are owned by ishanKulkarni13 until reassigned.

Resolved on 2026-10-28 and removed: OQ-02, OQ-03, OQ-04, OQ-06, OQ-20, OQ-22, OQ-23, OQ-25, OQ-27, OQ-28, OQ-29, OQ-31 (versions aside) and OQ-34; see ADRs [0023](0023-template-fork-adoption-model.md) to [0038](0038-frontend-stack.md).

Resolved on 2026-11-11 and removed: OQ-37 (the owner kept the name "Notes" for the example feature; [0052](0052-milestone-5-implementation-decisions.md)).

Answered by the owner on 2026-11-11 and recorded in draft ADRs, which are accepted separately: whether administrators write conditions (no: named conditions written in Java, [0054](0054-authorization-domain-model.md)); where evaluation lives (in Kibootia on MySQL, confirmed by measurement, [0056](0056-authorization-evaluation-engine.md)); who may assign roles (only roles whose permissions the assigner holds, [0054](0054-authorization-domain-model.md) rule 7). Removed because they are not Kibootia's to answer: six questions about indent-edi's own business rules from the requirements drafts (its permission matrix, approval levels, edits after submission, export rights, changes of ownership); they belong in indent-edi's own open-questions table.

Resolved on 2026-11-10 and removed: OQ-40 (RustFS 1.0.4 passed 12 contract tests, including presigned PUT and GET, in a local run and in CI run 42873216224; [0051](0051-milestone-4-implementation-decisions.md)).

Resolved on 2026-11-09 and removed: OQ-41 (Hibernate 7.4 generates UUIDv7, maps `BINARY(16)` and filters soft-deleted rows with no fallback; [0049](0049-milestone-2-implementation-decisions.md)) and the deviations OQ-12, OQ-15 and OQ-16 (JPA added, Java 25, Adminer pinned).

Resolved on 2026-11-08 and removed: OQ-13 (the owner rotated the committed credentials), OQ-43 (Spring Modulith 2.1.2 with the event publication registry on MySQL passed in CI, run 42477195261), OQ-10 (the dependency was removed from `pom.xml`), OQ-49 (the owner approved the milestone completion criteria), OQ-01 ([0005](0005-hand-written-model-mappers.md)), OQ-05 ([0021](0021-frontend-beside-backend-in-one-repository.md)), OQ-11 ([0048](0048-java-25-baseline-on-temurin.md)), OQ-14 ([0040](0040-local-development-topology-and-setup.md)), OQ-21 ([0039](0039-spring-modulith-module-boundaries-and-outbox.md)), OQ-24 ([0041](0041-user-lifecycle-and-account-model.md)), OQ-26 ([0043](0043-http-api-conventions.md)), OQ-30 ([testing strategy](../engineering/testing/strategy.md)), OQ-32 ([0047](0047-health-exposure-and-container-images.md)), OQ-33 ([application configuration](../operations/configuration/application-configuration.md)), OQ-35 ([0044](0044-authorization-enforcement-conventions.md)) and the decision part of OQ-43 ([0039](0039-spring-modulith-module-boundaries-and-outbox.md)). The working record is the [decision log](../temp/kibootia-decision-log.md).

## Checks required before a decision is final

Each check runs in the milestone that first needs it. A failed check leads to the named fallback, recorded in a new ADR.

| ID | Check | Milestone | Fallback | Decision it gates |
|---|---|---|---|---|
| OQ-42 | OTLP log export wiring in the chosen Spring Boot version. Finding (2026-11-09, Boot 4.1.1): the `management.opentelemetry.logging.*` properties exist, but the exporter needs OpenTelemetry's Logback appender (`opentelemetry-logback-appender-1.0`, alpha-versioned), which `spring-boot-starter-opentelemetry` does not include. Not wired in milestone 1; Next.js logs also go to the console only. Traces and metrics are exported | 1 | Console JSON logs only until wired (in use) | [ADR 0030](0030-otlp-telemetry-and-local-lgtm.md) |
| OQ-44 | The whole toolchain builds and tests on Java 25 (Spring Boot, Spring Modulith, ArchUnit, Testcontainers, Maven plugins, scanned image). Build and tests pass on Eclipse Temurin 25 in CI (2026-11-08, run 42477195261); the image scan remains (the `images` CI job is written; the images build and ran locally on 2026-11-09) | 0 | Java 21 | [ADR 0048](0048-java-25-baseline-on-temurin.md) |
| OQ-45 | In the `apps` Compose profile, Keycloak's fixed public hostname with an internal back channel works, and Spring validates the public issuer while fetching keys internally. Passes locally (a real sign-in through both containers and a unit-level test of the split in `ResourceServerIT`, 2026-11-09); the CI end-to-end job passed on 2026-11-09 (run 42589726681) | 1 | Applications on the host only | [ADR 0040](0040-local-development-topology-and-setup.md) |
| OQ-46 | The OpenTofu Keycloak provider manages the pinned Keycloak version; Keycloak runs on the pinned PostgreSQL. Passes locally (Keycloak 26.8.2, provider 5.10.0, PostgreSQL 17.11: apply of 8 resources and an in-place change, 2026-11-09); passed in the CI end-to-end job (run 42589726681, 2026-11-09) | 1 | Pin earlier compatible versions | [ADR 0045](0045-keycloak-operations.md) |
| OQ-47 | OpenTofu's native state locking works on the production S3 service recommended in the reference deployment | 6 | Root-only state file on the server | [ADR 0045](0045-keycloak-operations.md) |

## Undecided

| ID | Question | Blocks |
|---|---|---|
| OQ-36 | Production SMTP settings for Keycloak (any provider; the backend sends no mail, [ADR 0041](0041-user-lifecycle-and-account-model.md)) | [Milestone 6](../product/roadmap/milestone-6-production-readiness.md) documentation only |
| OQ-48 | Kibootia's licence; `pom.xml` has an empty licence | The first release in [milestone 6](../product/roadmap/milestone-6-production-readiness.md); not coding |

## Authorization: decisions awaiting the owner

Raised by the review of 2026-11-11 (`temp/authorization-review.md`). The recommendation column is the reviewer's and is not a decision.

| ID | Question | Recommendation | Blocks |
|---|---|---|---|
| OQ-80 | Are the nine proposed requirements, AUTH-041 to AUTH-049, approved? ([requirements](../product/requirements/authorization/README.md)) | Approve 044, 045, 046, 048 and 049 as they refine established requirements; approve 041, 042, 043 and 047 when their milestone is scheduled | Milestones 10 to 15 of the [authorization roadmap](../product/roadmap/authorization-roadmap.md) |
| OQ-81 | Is draft [ADR 0053](0053-adopt-authorization-requirements.md) accepted as written? It records the owner's choice of full commitment and supersedes ADR 0009 | Accept after reading its consequences | Everything below |
| OQ-82 | Is draft [ADR 0054](0054-authorization-domain-model.md), the domain model, accepted? | Accept | [Milestone 7](../product/roadmap/milestone-7-authorization-contract.md) |
| OQ-83 | Is draft [ADR 0055](0055-authorization-decision-contract.md), the decision contract, accepted? | Accept | Milestone 7 |
| OQ-84 | The engine evaluation of draft [ADR 0056](0056-authorization-evaluation-engine.md): in-house on MySQL against OpenFGA and Cerbos on two reference scenarios, with measurements | Run it during milestone 7; decide before milestone 9 | Milestone 9 onward |
| OQ-85 | Does [milestone 6](../product/roadmap/milestone-6-production-readiness.md) (production readiness and the first release tag) come before or after milestones 7 and 8? The owner said on 2026-11-11 that Kibootia has no forks and no users, so breaking changes are acceptable and the order is no longer forced | After milestone 8, so the first tag already carries the new contract; either order is safe | The order of work |
| OQ-86 | The 31 end-to-end tests of milestone 5 were skipped in both CI runs (42959703043, 42981706613) and have never run in CI | Run the workflow by manual dispatch, or check the next nightly run | Marking milestone 5 verified |
| OQ-87 | A record the caller may not view answers 404 ([ADR 0043](0043-http-api-conventions.md)), but another user's profile or avatar answers 403 ([ADR 0051](0051-milestone-4-implementation-decisions.md)). Which is the rule? | Keep 404 as the rule and record the profile case as a stated exception, or change it to 404 | Milestone 7 (reason codes) |
| OQ-88 | What "SDK" means in AUTH-037, given that nothing is published as a library ([ADR 0023](0023-template-fork-adoption-model.md)). The owner did not answer this directly; it follows from authorization being an in-process module | The in-repository Java contract and the OpenAPI-described administration API; confirm the wording | AUTH-037 |

## Authorization: questions carried over from the requirements drafts

Section 27 of `temp/authz-new-srs.md`, unchanged in meaning. Each is answered by the owner, not by an assumption in the foundation. "Needed by" is the phase that cannot be designed without the answer.

| ID | Question | Recommendation | Needed by |
|---|---|---|---|
| OQ-50 | Hierarchical structures and overlapping relationship graphs both in the initial implementation? (AUTH-016) | Yes to both in the model (ADR 0054); deliver hierarchy in milestone 9 and relationships in milestone 10 | Milestone 9 |
| OQ-51 | Which capabilities are mandatory in the initial foundation? | Only today's baseline: roles, permissions, ownership | Milestone 7 |
| OQ-52 | Which capabilities are optional modules? | Everything beyond the baseline, each with a switch | Every phase |
| OQ-53 | How much administrator customization is exposed by default? | Roles and assignments, as today; the rest is opt-in per application | Milestone 15 |
| OQ-54 | Which identity-provider integrations are supported initially? | Keycloak only, with the token-to-principal mapping behind one component | Milestone 12 |
| OQ-55 | How do synchronized group memberships interact with manual ones? | A group has one source: manual or synchronized, never both | Milestone 13 |
| OQ-56 | What is the lifecycle of a service account? | Registered by an administrator, enabled or disabled, never signs in interactively | Milestone 12 |
| OQ-57 | How do disabled or deleted identities affect their assignments? | Disabled: assignments kept and ignored. Erased: removed, as today | Milestone 9 |
| OQ-58 | Full tenant isolation in the initial release, or only its boundaries? | Boundaries only (today's state) until slice 14 | Milestone 16 |
| OQ-59 | Which cross-tenant administrative capabilities are permitted? | None without an explicit platform-level grant | Milestone 16 |
| OQ-60 | How does tenant-level configuration interact with application-level configuration? | The application defines; a tenant may only narrow | Milestone 16 |
| OQ-61 | Which changes need administrative approval? | Configurable; default none, with granting the administrator role suggested | Milestone 15 |
| OQ-62 | Does publishing configuration need one administrator or several? | One by default; a second is the approval capability | Milestone 15 |
| OQ-63 | What rollback guarantees are required? | Rollback restores definitions only and never an assignment | Milestone 15 |
| OQ-64 | How are policy conflicts shown to administrators? | At validation, before publication, naming the conflicting grants | Milestone 15 |
| OQ-65 | What audit retention periods are required? | Keep 365 days for changes (ADR 0046); a shorter default for decision records | Milestone 15 |
| OQ-66 | Which audit records must be immutable? | All of them: append-only, removed only by the retention purge | Milestone 15 |
| OQ-67 | How much detail of a decision is stored? | Principal, action, resource reference, outcome, reason code, trace ID; no attribute values | Milestone 7 |
| OQ-68 | What consistency is required after a revocation? | The next request, as today | Milestone 17 |
| OQ-69 | What happens when an external authorization dependency is unavailable? | Deny; with no external engine by default the case does not arise | Milestone 9 |
| OQ-70 | Decision latency targets? | Measure today's baseline in milestone 7 and set the target from it | Milestone 9 |
| OQ-71 | Expected numbers of users, groups, relationships and policies? | Take them from Timetia, the larger of the two applications | Milestone 9 |
| OQ-72 | Expected collection sizes for filtered queries? | As OQ-71 | Milestone 9 |
| OQ-73 | Acceptable trade-offs between caching and revocation? | None: revocation wins | Milestone 17 |
