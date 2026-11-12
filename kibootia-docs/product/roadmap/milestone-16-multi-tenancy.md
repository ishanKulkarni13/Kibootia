# Milestone 16: Live Multi-Tenancy

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists. The schema is tenant-ready with one default tenant ([ADR 0028](../../decisions/0028-tenant-ready-schema.md)), and the tenant filter and cross-tenant tests are verified.

A phase of the [authorization roadmap](authorization-roadmap.md). An ADR that replaces the "no live tenant resolution, no tenant management" paragraph of ADR 0028 is written when the phase starts.

## 1. Objective

Let one installation serve several tenants whose data, users, roles and administrators are fully separate, and let a single-tenant application ignore the whole capability. After this phase a request is resolved to a tenant, tenant administrators exist, and anything that crosses tenants needs an explicit platform-level grant.

## 2. Requirements covered

AUTH-017, AUTH-028 (tenant level). Security finding S7. No reference scenario: both applications list multi-tenancy as optional and neither describes it, so the scenarios are written from the requirement text. A tenant is a data boundary; Timetia's departments are scope nodes (milestone 9), not tenants.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| `platform/tenancy/TenantResolver` | Gains a shipped implementation that resolves from the request |
| `platform/tenancy/TenantContextFilter` | Unchanged in role; the resolved tenant is bound as today |
| `RetentionPurgeJob`, `ReconciliationJob` and any job naming the default tenant | Iterate the tenants that exist |
| `access` | Roles, scopes, groups, relationships and definitions are per tenant (already carry `tenant_id`); a platform-level role set is added |
| `identity` profiles | A profile belongs to a tenant; resolution decides which |
| Object storage keys, events | Already carry the tenant; verified with two tenants |
| Frontend | A tenant context in the session and the shell, only when the capability is on |

## 4. New functionality delivered

- A tenant table and a resolution strategy, configurable: a token claim, or the host name. One strategy ships; others are replaceable by the existing resolver interface.
- Tenant administrators with authority inside one tenant; platform administrators who manage tenants and may hold explicit cross-tenant grants.
- Tenant provisioning: creating a tenant seeds its built-in roles, scope root and settings in one transaction.
- Tenant-level configuration that may only narrow what the application allows (OQ-60).
- Per-tenant jobs and per-tenant retention.
- A switch for multi-tenancy, off by default. With it off, the single default tenant is used exactly as today and no tenant screen exists.

## 5. Modules affected

`platform` (tenancy, jobs), `access`, `identity`, `files`, `audit`, `notes`, the frontend shell and the administration screens, and the Keycloak configuration if the claim strategy is chosen.

## 6. Dependencies

Milestone 15, because tenant administrators and platform authority are administrator-hierarchy work, and the definition lifecycle must be per tenant from the start.

## 7. Database changes and migration strategy

Additive: a tenant table in `platform`, with the default tenant inserted from the constant that exists today so every current row already belongs to a valid tenant; a platform-level role set in `access`. Every foundation table already has `tenant_id`; this phase adds nothing to them. If a resolution strategy needs a mapping (host name to tenant), it is a column or table of its own.

## 8. Security implications

- This is the highest-impact isolation risk in the roadmap. A forgotten tenant condition in one native query leaks across tenants.
- The guard added in milestone 8, which fails on a hand-written SQL statement with no tenant parameter, is the first control; the second is tests that run **two live tenants** through every module's endpoints and assert that neither sees the other's data, counts, files, audit entries or decision records.
- Cross-tenant authority needs an explicit platform grant and is audited in both tenants.
- A wrong or forgeable resolution (a header an attacker controls) would choose the tenant for them. The claim strategy trusts only a validated token; the host strategy trusts only a configured host map; a request that resolves to no tenant is refused.
- Tenant administrators must not reach platform authority: the "only what you hold" rule, and the platform role set being separate, ensure it.
- A tenant's decision log and audit log are its own.

## 9. Automated tests required

- Two live tenants through every module: notes, files, avatars, users, roles, assignments, groups, audit, decisions. Lists, counts, detail, export, search and filter-options each tested.
- Resolution: valid claim or host resolves; unknown tenant refused; a forged header ignored; a user token for tenant A refused in tenant B.
- Jobs: each tenant's retention and reconciliation run; one tenant's failure does not stop the others.
- Provisioning: a new tenant has working built-in roles and can sign in its administrator.
- Platform authority: a platform grant works across tenants and is audited in both; a tenant administrator is refused platform actions.
- The guard against tenant-less SQL fails on a fixture that omits the parameter.
- The switch off: single tenant exactly as today; the application starts with no tenant screen.
- `module-removal`: removing a module leaves tenant provisioning intact.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), [transactions and data ownership](../../architecture/data/transactions-and-data-ownership.md), [persistence stack](../../architecture/data/persistence-stack.md), [goals and non-goals](../goals-and-non-goals.md), [application configuration](../../operations/configuration/application-configuration.md), the [threat model](../../operations/security/threat-model.md), the glossary entry for tenant, a recipe for the switch, [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), the tenancy ADR and an implementation-decisions ADR.

## 11. Acceptance criteria

1. The two-tenant suite passes for every module.
2. No endpoint, list, count or file URL returns another tenant's data.
3. The tenant-less SQL guard is in CI and shown to fire.
4. With the switch off, every test of the previous phases passes unchanged.
5. All of the above pass in CI.

## 12. Rollback and recovery

The schema change is additive and the default tenant is unchanged, so turning the switch off returns to single-tenant behavior. Data created for other tenants stays in place but is unreachable with the switch off, and the recipe says so and says how to export a tenant before switching off. Provisioning is transactional, so a failed tenant creation leaves nothing behind.

## 13. Usable state at the end of the phase

One installation can host separate organizations with their own administrators. An application that wants one organization sets nothing and sees no difference.
