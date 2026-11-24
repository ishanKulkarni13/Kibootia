# Architecture Enforcement

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** verified for rules 1 to 11 (`ModularityTest`, `ArchitectureTest` and `ArchitectureRulesFireTest` in the backend CI job, run 42959703043, 2026-11-11). Rule 12 is observed (verified, CI run 45354022559, 2026-11-24). Rule 13 is proposed ([ADR 0058](../decisions/0058-no-external-call-inside-a-database-transaction.md), draft).

Decisions: [ADR 0013](../decisions/0013-archunit-architecture-enforcement.md), [ADR 0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md), [ADR 0044](../decisions/0044-authorization-enforcement-conventions.md).

## Purpose

Record the architecture rules that are meant to be enforced by automated tests, so they do not live only in these documents.

## Two tools

- **Spring Modulith** verifies the module level: no cycles between modules, and no module reaches into another module's private sub-packages.
- **ArchUnit** verifies rules inside modules and project-wide conventions.

## Rules

Decided rules. Rules 1 and 4 to 11 are implemented in `ArchitectureTest` (rule 7 and rules 9 to 11 in helper classes next to it); rules 2, 3 and 12 are covered by `ModularityTest`; rule 14 by `TenantSqlRulesTest`. Startup checks complete them: an undeclared action in `@RequiresPermission`, and the annotation on an action on a record, stop the application (`AccessConfigurationTest`). Package selectors follow [package structure](../engineering/conventions/package-structure.md).

| # | Rule |
|---|---|
| 1 | Application logic must not depend on concrete infrastructure implementations (the `web`, `persistence`, `keycloak` and `s3` packages) |
| 2 | A feature must not access another feature's private persistence implementation (Spring Modulith) |
| 3 | There are no circular dependencies between features (Spring Modulith) |
| 4 | Designated domain packages must not depend on infrastructure frameworks |
| 5 | Optional capabilities must not introduce unnecessary dependencies; core code does not depend on an optional or default-on module ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)). Implemented for every module in `REMOVABLE_MODULES` (`files` since milestone 4, `notes` since milestone 5): nothing outside it depends on it, and other modules use the `platform.files` port ([ADR 0051](../decisions/0051-milestone-4-implementation-decisions.md), [ADR 0052](../decisions/0052-milestone-5-implementation-decisions.md)). A new removable module is added to that list |
| 6 | Persistence entities are not used in API types ([ADR 0004](../decisions/0004-separate-api-application-and-persistence-models.md)) |
| 7 | Every public use-case method carries an authorization check or an explicit `@NoAuthorization("reason")` ([ADR 0044](../decisions/0044-authorization-enforcement-conventions.md)) |
| 8 | The `platform` module contains no business logic and depends on no other module. Proposed (an inference from the module map, not an owner decision); implemented for the dependency half (`platform` is declared with no allowed dependencies and ArchUnit checks it); "no business logic" is not machine-checkable |
| 9 | Every `@Scheduled` method carries `@SchedulerLock`, because an unlocked job runs on every instance at once ([ADR 0031](../decisions/0031-transactional-outbox-and-locked-jobs.md), [ADR 0049](../decisions/0049-milestone-2-implementation-decisions.md)) |
| 10 | No method uses `@ApplicationModuleListener`: it opens its transaction before the tenant is bound. Durable listeners use `@DurableEventListener` |
| 11 | A `@DurableEventListener` method takes a `DomainEvent` as its first parameter, because the tenant and trace are restored from the event's context |
| 12 | Every module declares `allowedDependencies`, and Spring Modulith fails the build on a dependency that is not listed, so the graph between business modules is a reviewed artefact ([ADR 0057](../decisions/0057-consumer-owned-ports-and-declared-module-dependencies.md)). Implemented by the `package-info.java` of each module and `ModularityTest`; shown to fire by adding an undeclared edge in a scratch copy (2026-11-24) |
| 14 | Hand-written SQL never forgets the tenant (finding S7): a literal naming a table with `tenant_id` carries a `tenant_id` condition (an insert names the column); an open `where` is allowed only after a builder that starts with the tenant; a deliberate all-tenant statement carries the marker `// sql: every tenant` with a reason. `TenantSqlRules`, shown to fire in `TenantSqlRulesTest` (branch `m-8`, local run 2026-11-24, CI run 45384428905). It reads source text, so SQL assembled from constants in other files is out of its reach |
| 13 | Proposed: a class that depends on `IdentityProviderAdmin` or `ObjectStorage` has no `@Transactional` method, so no database transaction is open while an external system is called ([ADR 0058](../decisions/0058-no-external-call-inside-a-database-transaction.md)). Not implemented |

## Beyond ArchUnit

The module-removal check is a CI job, not an ArchUnit rule: the application must build and start with each default-on module disabled or removed. The job `module-removal` runs it ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0052](../decisions/0052-milestone-5-implementation-decisions.md), [recipes](../recipes/README.md)). Rule 5 is what makes removal possible; the job is what proves it.

## Limits

Rules about features can only be demonstrated with two or more modules; the six modules of the foundation and the example feature supply them since [milestone 5](../product/roadmap/milestone-5-example-feature.md). A rule that says "no class may..." holds just as well for a typo or for a module that was removed, so each rule that allows an empty match is also pointed at classes made to break it and at classes made to satisfy it (`ArchitectureRulesFireTest`, fixtures under `platform.archfixtures` in the test sources). Spring Modulith, which owns rules 2 and 3, analyses only main classes, so a broken module cannot be fed to it that way; `ModularityTest` shows instead that the core modules depend on each other (and `platform` on none), so there are edges for the verification to check. Rule 7 does not see a check made inside a lambda or a helper class.

## Verification

A rule counts as enforced only when its ArchUnit test exists, runs in CI and passes ([verification standard](../engineering/testing/verification-standard.md)). How exceptions to a rule are handled is not decided.
