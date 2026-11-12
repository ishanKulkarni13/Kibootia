# Milestone 8: Grant Model

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists.

The second phase of the [authorization roadmap](authorization-roadmap.md). Decisions: draft [ADR 0054](../../decisions/0054-authorization-domain-model.md) and [ADR 0055](../../decisions/0055-authorization-decision-contract.md) (point 5).

## 1. Objective

Replace "a role is a list of permission names, where a name ending in `-any` means every record" by "a role is a list of grants, where each grant says how far it reaches". After this phase the reach of a permission is data an administrator can see and choose, which is what scopes, relationships and conditions are later added to.

## 2. Requirements covered

AUTH-001 (resource types and action kinds), AUTH-003 (the single model), AUTH-010 (action separated from grant), AUTH-038. Security findings S4 and S7, and the administrator-boundary item of S9.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| `platform/authorization/Permission`, `PermissionProvider` | A module declares actions (collection or instance) and resource types |
| `access/domain/PermissionRegistry` | The `-any` suffix logic is removed; it validates actions and resource types |
| `access/persistence` (`RoleEntity`, `JpaRoleStore`, `StoredPermissionSource`) | Read and write grants |
| `access/application/RoleService`, `BuiltInRoles` | Edit grants; the administrator becomes a role with explicit grants; the built-in user role is seeded with grants |
| `notes/NotesPermissions` and every module's permission class | `-any` permissions removed |
| `platform/authorization/Owned`, `TenantBound` | Removed; a declared resource type supplies the resource reference |
| `GET /api/v1/me`, `GET /api/v1/permissions`, the role endpoints | Return actions with a reach |
| `frontend/src/features/identity/permissions.tsx`, `features/access/RoleEditor.tsx` | `has(action)` plus the reach; a reach selector per grant |

## 4. New functionality delivered

- Resource types declared per module, with the way to build a resource reference from a record.
- Each action declared as collection (create, list) or instance (view, update, delete).
- Grants: an action and a reach, "everything in scope" or "by relation `owner`". In this phase the only scope is the tenant and the only relation is `owner`.
- Deny grants, behind a switch that is off by default.
- Startup validation: an undeclared resource type or action stops the application; `@RequiresPermission` on an instance action stops the application.
- The role editor shows and edits the reach; the "only what you hold" rule covers reach.
- An architecture rule or startup check that fails on a hand-written SQL statement with no tenant parameter.

## 5. Modules affected

`platform`, `access`, `identity`, `audit`, `files`, `notes`, and the frontend features `access` and `identity`. The OpenAPI file and the generated client change.

## 6. Dependencies

Milestone 7. No external dependency.

## 7. Database changes and migration strategy

Two Flyway migrations in the `access` folder, in this order:

1. Create `role_grant` (tenant, role, action, reach, effect) and fill it from `role_permission` by the rule of ADR 0054: a stored `x-any` becomes a grant of `x` with reach "everything in scope"; a stored `x` that has an `-any` sibling becomes a grant of `x` with reach "by relation `owner`"; any other stored `x` becomes a grant with reach "everything in scope". The built-in administrator role receives an explicit grant for every declared action. `role_permission_seed` is converted the same way.
2. Drop `role_permission`, in a separate migration that is merged only after the equivalence test passes in CI.

The application reads only `role_grant` from the first migration on. A stored name that no module declares is ignored, as today, so removing a module still cannot break a role.

## 8. Security implications

- The migration is the one step in the roadmap that can silently change access. The equivalence test is the control.
- The administrator no longer holds permissions implicitly. A permission a module adds later must be granted to it by the same seeding that offers new permissions to the user role, or administrators lose access to new features; a test covers a module adding an action.
- Deny grants exist but are off; when on, a deny always wins.
- Closes the trap where a method-level check passed with own-records reach.

## 9. Automated tests required

- **Equivalence:** load a database in the milestone 7 shape containing the built-in roles and several custom roles (own-only, any, mixed, with a no-longer-declared permission), migrate, and assert that for every user, action and sample record the decision is identical before and after.
- Migrate-from-empty still passes (`BackendApplicationIT`).
- Startup fails for each invalid declaration, one test each (`AccessConfigurationTest` extended).
- `AccessApiIT`: create and edit roles by grant; an editor cannot choose a reach wider than their own; the administrator role's grants are complete after a module adds an action.
- `NotesApiIT` and the notes end-to-end story pass with the `-any` permissions gone.
- The removal script still leaves a clean application: `remove-module.mjs notes` and the four `module-removal` rows.
- Deny grants: off by default; when on, a deny beats an allow from another role.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), [how to add a feature](../../engineering/guides/add-a-feature.md) (declaring resource types and actions), the glossary (grant, reach, resource type, action), [architecture enforcement](../../architecture/architecture-enforcement.md) (the new checks), the [remove notes](../../recipes/remove-notes.md) recipe, a recipe for the deny-grant switch, [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), and an implementation-decisions ADR.

## 11. Acceptance criteria

1. The equivalence test passes for built-in and custom roles.
2. No class, test, script or document outside history refers to an `-any` permission.
3. `Owned` and `TenantBound` no longer exist; the temporary bridge of milestone 7 is gone.
4. The role editor shows a reach for each grant and refuses a reach the editor does not hold.
5. The application starts with deny grants on and with them off.
6. The query count per decision is not higher than the milestone 7 baseline.
7. All of the above pass in CI.

## 12. Rollback and recovery

Before the second migration is merged, rollback is reverting the code: `role_permission` is still intact and still correct, because nothing writes to it. After the drop, recovery is from a database backup, which is why the drop is a separate, later change. Kibootia has no production installation, so no user data is at risk during this phase.

## 13. Usable state at the end of the phase

Kibootia builds, starts and gives every existing user exactly the access they had. Administrators see, for each permission in a role, whether it covers every record or only the holder's own, and can change it. The example feature and its guide show the new declarations. Nothing yet uses scopes, relationships or conditions.
