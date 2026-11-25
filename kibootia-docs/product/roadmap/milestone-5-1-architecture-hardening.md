# Milestone 5.1: Architecture Hardening

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** built in large part and verified in CI on 2026-11-25. **Done:** R-01 (role assignment), R-02 (declared dependencies), R-05 and R-06 ([ADR 0058](../../decisions/0058-no-external-call-inside-a-database-transaction.md), CI run 45522177552), R-07 ([ADR 0059](../../decisions/0059-data-integrity-in-the-database.md), 45477165322), R-09 ([ADR 0061](../../decisions/0061-ci-quality-and-supply-chain-gates.md), 45473171226), R-10 (metrics, 45524869886), R-11 (decided in [ADR 0069](../../decisions/0069-stay-on-mysql-until-multi-tenancy.md)), R-12 ([ADR 0063](../../decisions/0063-bff-and-edge-hardening.md), 45478941337) and R-16. **Not done:** R-03 (consumer-owned ports, the rate-limit registry, the storage SPI and the configuration keys; [ADR 0057](../../decisions/0057-consumer-owned-ports-and-declared-module-dependencies.md) items 2 to 6, still a draft), R-08 beyond the single status document (items 2 to 6 of [ADR 0060](../../decisions/0060-implementation-status-in-one-place.md)), R-13 and R-14 (the split of `identity/application`, the frontend import cycle, generated permission constants), the request-throughput baseline of R-15, OTLP log export (OQ-42), and acceptance criterion 5 (each metric visible in Grafana: there is no dashboard). What each ADR changed while it was built is in its Status section.

## 1. Objective

Close the structural, data and operational gaps found in the [architecture assessment](../../architecture/architecture-assessment.md) before the authorization phases add tables, modules and rules on top of them. No user-visible behaviour changes except one security fix. The numbering "5.1" says that it belongs after milestone 5 and before milestone 7; the numbers of the other milestones do not change.

## 2. Requirements covered

| Assessment | Recommendation | Decision |
|---|---|---|
| S-1 | R-01 role assignment requires holding the role's permissions | **Done** |
| C-3 | R-02 declared module dependencies | **Done**, [ADR 0057](../../decisions/0057-consumer-owned-ports-and-declared-module-dependencies.md) item 1 |
| Q-6 | R-16 repository files | **Done** (the licence is Apache-2.0, ADR 0068) |
| C-1, C-2, E-1 | R-03 consumer-owned ports, rate-limit registry | ADR 0057 |
| T-1, T-2, T-3 | R-05, R-06 no external call in a transaction; Keycloak retry | [ADR 0058](../../decisions/0058-no-external-call-inside-a-database-transaction.md): **built** except the sweep of orphan Keycloak accounts, which is replaced by a durable cleanup event (reason in the ADR) |
| D-1 to D-5, T-6 | R-07 constraints, event versioning, failed-event retention, files-off guard | [ADR 0059](../../decisions/0059-data-integrity-in-the-database.md): **built** except the unique email key, which is not built and why is in the ADR |
| P-1, P-2 | R-08 one place for status | [ADR 0060](../../decisions/0060-implementation-status-in-one-place.md) |
| Q-1, Q-2, Q-5 | R-09 CI gates | [ADR 0061](../../decisions/0061-ci-quality-and-supply-chain-gates.md): **built** except signing (milestone 6) and the Java formatter (not adopted) |
| Q-3 | R-10 custom metrics | none needed: **built**; acceptance criterion 5 (each metric visible in Grafana) is not met, because no dashboard exists |
| D-8, D-9 | R-11 database evaluation | [ADR 0062](../../decisions/0062-reassess-the-application-database-before-multi-tenancy.md): **decided** in [ADR 0069](../../decisions/0069-stay-on-mysql-until-multi-tenancy.md) (stay on MySQL; the evaluation is run before milestone 16; only the cost criterion was measured) |
| B-1 to B-3, E-3 | R-12 edge hardening | [ADR 0063](../../decisions/0063-bff-and-edge-hardening.md): **built** except back-channel logout (milestone 6) and the backend's reading of the client address |
| C-6, C-7, C-8, Q-4, T-4 | R-13 to R-15 cohesion, frontend structure, tests | none needed |

## 3. Existing implementation affected

`platform.directory`, `platform.ratelimit`, `platform.config`, `platform.events` (interceptor and annotation), the retention job, `files` (purge and events), `identity` (`UserEvents`, `UserAdministration`, the Keycloak adapter), `access` (last-administrator rule, foreign keys), `audit` (viewer port), the frontend proxy, session and configuration, and `ci.yml`.

## 4. New functionality delivered

- Consumer-owned ports; `UserDirectory` removed; the last-administrator rule in `identity`.
- Durable handlers that do not hold a transaction while calling an external system; a purge that deletes objects outside its transaction; bounded retry for Keycloak; a durable cleanup of orphan Keycloak accounts.
- Foreign keys inside modules; a unique email key; event schema versions and golden-file tests; failed-event purge and a gauge.
- Custom metrics: failed and exhausted events, authorization denials (counted at the decision point), Keycloak call duration and failures, purge counts, rate-limit rejections, S3 errors.
- CI gates in stages; a documentation check.
- Frontend: no `identity`/`access` import cycle, generated permission constants, shell parts moved out of `features/`.
- Edge hardening of ADR 0063, except back-channel logout.
- A time-boxed PostgreSQL evaluation with a written outcome.
- An upgrade test (previous schema, then the new migrations), an event-compatibility test and a request-throughput baseline.

## 5. Modules affected

Every backend module and the frontend; the load of each item is in the ADR it comes from.

## 6. Dependencies

Docker for the integration tests. No new library is assumed: the retry support of Spring Framework 7 and Trivy's SBOM output are checked first, and any dependency that remains is recorded in the ADR that needs it ([ADR 0035](../../decisions/0035-version-adoption-policy.md)).

## 7. Database changes and migration strategy

New migrations, each in the owning module's folder, each tested from the previous schema: foreign keys in `access`; a unique email key in `identity`; `event_publication.serialized_event` to `TEXT`; a failed-event retention index. A migration that would fail on existing data has a pre-check that names the offending rows.

## 8. Security implications

Improves: S1 (done), the edge (CSP, HSTS, header allow-list, client address), supply chain (SBOM, code scanning, pinned actions) and invariants held by the database. Adds: trust in forwarded client addresses, which is a deployment setting and gets a threat-model entry.

## 9. Automated tests required

- Allowed and denied tests for the role-assignment rule (**done**: `AccessApiIT.assigningCannotEscalate`, `UserAdministrationIT.invitingCannotEscalate`).
- `ModularityTest` for every declared dependency (**done**).
- A test per handler with `transactional = false` that runs it twice, and one that kills it between the effect and the mark.
- An architecture rule 13 shown to fire: a class that depends on `IdentityProviderAdmin` or `ObjectStorage` has no `@Transactional` method.
- An integration test that a slow fake Keycloak does not hold a database connection.
- Concurrency test for two administrators removing each other, unchanged, after the rule moves.
- Golden-file deserialisation test for every event type.
- A two-instance rate-limit behaviour test, or the documented limit if it is accepted as per instance.
- Frontend: proxy timeout, body limit, header allow-list, nonce present, session key rotation.

## 10. Documentation updates

[Module catalogue](../../architecture/module-catalogue.md), [data inventory](../../architecture/data/data-inventory.md), [architecture enforcement](../../architecture/architecture-enforcement.md) (rules 12 and 13), [persistence stack](../../architecture/data/persistence-stack.md) (foreign key convention), [communication](../../architecture/communication.md) (handlers and transactions), threat model, configuration reference, [implementation status](implementation-status.md).

## 11. Acceptance criteria

1. Every ADR it relies on is accepted, or the item is removed from this milestone.
2. `./mvnw verify`, the frontend pipeline and the `module-removal` job pass in CI, with run IDs recorded in implementation status.
3. The 31 end-to-end tests of milestone 5 have run in CI (OQ-86). **Met:** CI run 45354022559, 2026-11-24.
4. No class that depends on an external-system port has a transactional method (rule 13).
5. Each custom metric appears in the local Grafana, shown by a test or a recorded query.
6. The PostgreSQL evaluation has a written outcome as an ADR.

## 12. Rollback and recovery

Each item is its own commit and can be reverted alone. Migrations are forward-only and tested from the previous schema; a rejected foreign key is dropped by a new migration, not by editing an applied one ([ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md)).

## 13. Usable state at the end of the phase

The application starts from a fresh clone and from the schema of milestone 5, behaves as before for users, and has the guarantees above. If work stops after any item, the others remain valid because they are independent.
