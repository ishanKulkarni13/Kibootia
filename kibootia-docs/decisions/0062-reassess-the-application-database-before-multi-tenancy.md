# 0062 Reassess the application database before multi-tenancy

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** planned. This is an evaluation ADR: it decides how the question will be answered, not the answer. Observed today: MySQL 8.4 for the application, PostgreSQL 17 for Keycloak only.

## Context

[ADR 0011](0011-mysql-jpa-hibernate-persistence.md) chose MySQL. Since then the project decided to commit to live multi-tenancy ([ADR 0053](0053-adopt-authorization-requirements.md), milestone 16), scope hierarchies and groups with recursive queries (milestones 9 and 13), and a larger set of tables that each carry a tenant column. Three consequences of MySQL are now visible:

- **Tenant isolation is by hand.** MySQL has no row-level security, so every native query needs its `tenant_id` condition written and reviewed (D-8, D-9 of the [assessment](../architecture/architecture-assessment.md); S7 of the [security review](../temp/authorization-security-review.md)). No test catches a forgotten condition.
- **Workarounds for features PostgreSQL has.** Soft-delete uniqueness uses a generated `active_marker` column where PostgreSQL has partial unique indexes; identifiers are `BINARY(16)` with `unhex()` in SQL where PostgreSQL has a `uuid` type; the failed-events query uses `json_extract` on an unindexed column where PostgreSQL can index `jsonb`.
- **Two engines to operate.** Keycloak already needs PostgreSQL, so a deployment runs, backs up and upgrades two database engines.

The cost of changing is lowest now: there are nine migrations, no production data and, by the owner's statement of 2026-11-11, no forks or users. It rises with every table added in milestones 8 to 16.

## Decision

Run a time-boxed evaluation (two working days, in [milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md)), then decide before milestone 8 adds tables. The evaluation ports the existing migrations and the persistence tests to PostgreSQL on a branch and measures against these criteria:

| Criterion | Question |
|---|---|
| Tenant isolation | Does row-level security with a per-transaction tenant setting work with Hibernate's `@TenantId` and the connection pool, and does it make a forgotten condition harmless? |
| Fit | Do UUIDv7 (`uuid`), partial unique indexes, `jsonb` and recursive queries simplify the code, or only move the workarounds? |
| Spring Modulith | Does the JPA event publication registry work on PostgreSQL with the existing migration? |
| Cost | How many of the 213 main classes, 9 migrations and the SQL strings change? Which tests change? |
| Operations | One engine, one backup procedure; does Keycloak's PostgreSQL stay separate, or share a server with its own database? |

**Recommendation, with weaker evidence than the other ADRs of this round**: the lean is to move, because of timing and because it answers S7 structurally. This is a judgement about code that has not been ported; the evaluation exists to test it. The outcome is a new ADR that either supersedes ADR 0011 or records why it stays.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Stay on MySQL and add an ArchUnit or startup check for tenant-less SQL | Cheaper, and planned in milestone 8 as a fallback; it checks only the strings the check recognises and does not make a forgotten condition harmless |
| Schema or database per tenant | Left to applications by [ADR 0028](0028-tenant-ready-schema.md); heavy operations for the starter |
| Decide now without a spike | The effort and the Hibernate interplay are unmeasured |

## Consequences

- If the answer is to move, milestone 8 starts on the new engine and nothing needs a data migration.
- If the answer is to stay, the tenant-less SQL check becomes a requirement of milestone 8, not an option.
- Either way the decision is recorded before the schema grows.

## Status

draft. Awaiting the owner. OQ-90 in [open questions](open-questions.md).

## Related

[Architecture assessment](../architecture/architecture-assessment.md) (D-8, D-9), [ADR 0011](0011-mysql-jpa-hibernate-persistence.md), [ADR 0028](0028-tenant-ready-schema.md), [ADR 0056](0056-authorization-evaluation-engine.md), [milestone 16](../product/roadmap/milestone-16-multi-tenancy.md).
