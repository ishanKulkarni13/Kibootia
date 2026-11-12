# Milestone 9: Scoped Assignments

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists.

A phase of the [authorization roadmap](authorization-roadmap.md). Decision: draft [ADR 0054](../../decisions/0054-authorization-domain-model.md); an ADR on how scope nodes and their hierarchy are stored and queried is written when the phase starts, using the result of the engine measurement (draft [ADR 0056](../../decisions/0056-authorization-evaluation-engine.md)).

## 1. Objective

Let a role apply somewhere and for some time: "administrator of the Physics department until June" instead of "administrator". After this phase an assignment has a scope node and an optional validity period, a record can lie in several scope nodes, and lists are filtered by scope inside the database.

## 2. Requirements covered

AUTH-004, AUTH-007, AUTH-008, AUTH-016 (records in several scopes), AUTH-021, AUTH-024, AUTH-045, AUTH-051. Reference scenarios T1, T3, T8, T10 and E2 to E4, E7.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| `access` role assignment (`RoleAssignmentService`, `JpaRoleStore`, `UserRolesController`) | An assignment carries a scope node, a start, an expiry and a suspended flag |
| `access/domain` decision point and `StoredPermissionSource` | Evaluate "everything in scope" against the record's scope nodes; ignore assignments that are not valid now |
| The access filter of milestone 7 | Gains the alternative "records under these scope nodes" |
| Resource type declarations of milestone 8 | A resource type may declare how its records map to scope nodes |
| The last-administrator safeguard | Defined for the tenant root; scoped administrators are not covered by it |
| `frontend/src/features/access/UserRolesEditor.tsx` | Scope and validity when assigning |

## 4. New functionality delivered

- Scope types declared by the application in code, with their parent order (for example department, division, batch). Kibootia ships none.
- Scope nodes as stored data, created and arranged through an administration API by whoever holds the right at the parent node.
- A record's set of scope nodes, declared by its resource type.
- Assignments at a scope node, with start, expiry, suspension and provenance. Expiry is evaluated at decision time with the platform clock, in UTC.
- Inheritance down the hierarchy, declared per scope type and switchable per action; default off.
- The origin of a permission can be explained: which assignment, at which node, through which role.
- List filters by scope for any module that declares scopes.
- The assignment rule of milestone 7 extended: a user assigns a role at a node only if they hold its grants at that node or above.
- A switch for the whole capability. With it off, only assignments at the tenant root count, as after milestone 8; an assignment at any other node is ignored and grants nothing.

## 5. Modules affected

`platform` (contract additions), `access`, the frontend `access` feature. `notes` is unchanged: it declares no scope type and stays at the tenant root. The test fixture module gains a scope type so the scenarios can run.

## 6. Dependencies

Milestone 8. The engine measurement must be finished and draft ADR 0056 accepted or replaced, because this is where hierarchy queries are first written.

## 7. Database changes and migration strategy

In the `access` migration folder:

1. New tables for scope nodes and for the ancestor relation between them, both carrying `tenant_id`. One root node per tenant is inserted.
2. `role_assignment` gains a surrogate key, a scope node (filled with the tenant root for every existing row), `valid_from`, `valid_until` and `suspended_at`; its uniqueness becomes principal, role and scope node.
3. Where a module declares scopes, its own migration adds the link between its records and scope nodes; no foundation table is touched for that.

Each step is additive and leaves existing rows valid, so the application of milestone 8 would still read the table correctly between steps.

## 8. Security implications

- An assignment at one node must never reach a sibling: tested per scenario, including a user who is administrator in one department and plain member in another.
- The ancestor relation is derived data. If it is wrong, access silently widens or narrows, so a test rebuilds it from the parent links and compares.
- Inheritance is off unless declared, so adding a hierarchy never grants anything by itself (AUTH-007).
- Expiry checked at decision time leaves no window in which an expired assignment still works.
- Counts and "filter options" endpoints use the same filter as lists, or they leak what the list hides.

## 9. Automated tests required

- Scenarios T1, T3, T8, T10, E2 to E4 and E7 as tests against the fixture module.
- An equivalence test for the assignment migration: every user's access is identical before and after.
- Validity: not yet valid, valid, expired, suspended, and a change of the clock across the boundary.
- Inheritance on and off for the same hierarchy; an action opted out of inheritance.
- Assignment by a scoped administrator: allowed inside their node, refused outside, refused for a role they do not hold there.
- `TwoInstancesIT` extended: a scoped assignment and its revocation apply on the next request on a second instance.
- Lists: paging and totals are correct under a scope filter; a record in two scopes appears once.
- The capability switch off: the application starts and behaves as after milestone 8 (a row in `module-removal`).
- Query count per decision and per list, compared with the baseline.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), the [authorization scope](../../shared/glossary/authorization-scope.md) glossary entry and new entries (scope node, assignment), [how to add a feature](../../engineering/guides/add-a-feature.md) (declaring scopes for a resource type), a recipe for the switch, the [threat model](../../operations/security/threat-model.md), [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), the storage ADR and an implementation-decisions ADR.

## 11. Acceptance criteria

1. The named scenarios pass without any Timetia or indent-edi term in foundation code.
2. The migration equivalence test passes.
3. The example feature (Notes) is unchanged in behavior and its tests pass untouched.
4. With the switch off, every existing test of milestone 8 passes.
5. The measured cost per decision and per list is recorded, and a target is set from it (OQ-70).
6. All of the above pass in CI.

## 12. Rollback and recovery

The schema changes are additive. Turning the switch off returns to the behavior of milestone 8 without a deployment: assignments at the tenant root count and every other assignment is ignored, which can only narrow access. Reverting the code instead is not safe by itself, because the milestone 8 code would read a scoped assignment as tenant-wide and so widen access; the recipe therefore says to turn the switch off, or to remove scoped assignments, before any downgrade.

## 13. Usable state at the end of the phase

An application can declare its own hierarchy, give a role at a node of it for a period, and list only what the caller's scopes cover. An application that declares nothing behaves exactly as before. Everything indent-edi's matrix asks for (none, own, department, all) can be expressed.
