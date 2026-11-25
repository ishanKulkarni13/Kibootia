# 0069 Stay on MySQL until live multi-tenancy

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** not applicable (a decision record). The scan it rests on can be repeated with the commands in the Context.

## Context

[ADR 0062](0062-reassess-the-application-database-before-multi-tenancy.md) asked for a time-boxed port of the migrations and persistence tests to PostgreSQL, measured against five criteria, before milestone 8 added tables. Milestone 8 was built without it. The evaluation was not run as a port: it is two days of work whose result decides a change that nothing forces today, and it was not run on 2026-11-25, when the owner delegated the open decisions.

What was measured instead is only the **cost** criterion, by reading the code on 2026-11-25 (`grep` over `backend/src`, so a lower bound on the places that change):

| What | Count |
|---|---|
| Migration files; of them, files using MySQL-only syntax | 12; 9 |
| `BINARY(16)` columns (UUIDs); `DATETIME(6)` columns | 36; 17 |
| Generated columns for the soft-delete marker (`GENERATED ALWAYS AS`); `TINYINT`; `ALTER ... MODIFY` | 2; 2; 1 |
| Inline `INDEX` and `UNIQUE KEY` definitions | 12; 4 |
| Statements in main code using `INSERT IGNORE` | 5 |
| Multi-table `DELETE ... FROM ... JOIN` | 3 |
| JSON functions (the tenant of a failed event, read from its payload) | 3 |
| Main classes; files with native SQL | 231; 11 |
| Test files using MySQL-only SQL (`unhex`, `utc_timestamp`, `insert ignore`) | 17 |

The other four criteria (tenant isolation with row-level security and `@TenantId`, fit of UUIDv7 and partial indexes, the Spring Modulith registry, operations) were **not tested**. Whatever is said about them below is judgement, and the ADR it replaces already called its own lean "weaker evidence than the other ADRs of this round".

## Decision

1. **The application stays on MySQL 8.4** ([ADR 0011](0011-mysql-jpa-hibernate-persistence.md) stands). The evaluation of ADR 0062 is not run now.
2. **It is run before milestone 16** (live multi-tenancy), when row-level security is a real candidate for the isolation guarantee, and it is the first task of that milestone. Its inputs are the criteria and the table above, repeated.
3. **Until then the cost is kept from growing.** New migrations and new native SQL use the form that both databases accept where one exists, and say so in a comment where they use MySQL-only syntax. They do not add JSON functions, `INSERT IGNORE` or multi-table deletes. MySQL-only SQL stays inside the persistence classes and the migrations, as it is today, so a port touches those and not the use cases.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Run the full port now | Two days for a decision nothing forces; milestones 9 to 15 add tables on either engine, and the table above shows the port is bounded: a few hundred statements, not a rewrite |
| Move to PostgreSQL now without evaluating | The lean in ADR 0062 was untested; moving costs the 17 test files and the migrations for an isolation guarantee that is needed only when tenants are live |
| Never move; drop the evaluation | Row-level security may be the only way to make a forgotten tenant condition harmless, which matters at milestone 16 |

## Consequences

- No change to the code. The migrations and the tests keep their MySQL forms.
- OQ-90 is closed by this decision. The evaluation remains owed, with a date: before milestone 16.
- Each phase that adds tables adds to the cost table, by a small and recorded amount.

## Status

accepted on the owner's delegation of 2026-11-25. ADR 0062 is accepted with this outcome.

## Related

- [ADR 0011](0011-mysql-jpa-hibernate-persistence.md), [ADR 0028](0028-tenant-ready-schema.md), [ADR 0062](0062-reassess-the-application-database-before-multi-tenancy.md).
- [Architecture assessment](../architecture/architecture-assessment.md) (D-8, D-9), [milestone 16](../product/roadmap/milestone-16-multi-tenancy.md).
