# Milestone 7: Authorization Decision Contract

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists. The contract it replaces is verified (CI run 42605511477) and stays in use until this milestone is complete.

The first phase of the [authorization roadmap](authorization-roadmap.md). Decisions: draft [ADR 0053](../../decisions/0053-adopt-authorization-requirements.md), [ADR 0054](../../decisions/0054-authorization-domain-model.md) and [ADR 0055](../../decisions/0055-authorization-decision-contract.md). It does not start until all three are accepted and the owner authorizes implementation.

## 1. Objective

Change the shape of the question and the answer that every module uses, **without changing the database and without changing what any user can do**, except to close one privilege-escalation gap. After this phase a decision carries a reason, a module rule can no longer widen access, and a refusal leaves a record.

## 2. Requirements covered

AUTH-020 (conflict resolution), AUTH-034 (one interface), AUTH-031 and AUTH-036 (enforcement, kept and tightened), the denial part of AUTH-022, the assignment rule of AUTH-028. Security findings S1, S3, S8, S11 and S13 of the [security review](../../temp/authorization-security-review.md).

## 3. Existing implementation affected

| Code | Change |
|---|---|
| `platform/authorization/PolicyDecisionPoint`, `Authorization` | The port answers with a decision; `can` and `require` keep their signatures |
| `platform/authorization/Caller` | Becomes a typed principal (user or system; service arrives in milestone 12) |
| `platform/authorization/Scope` | Replaced by an access filter that, in this phase, has the same three outcomes: nothing, everything in the tenant, owned by the caller |
| `platform/authorization/ResourcePolicy` | Becomes a narrowing rule; all applicable rules are consulted |
| `access/domain/DefaultDecisionPoint` | Rewritten to the combination rule; same outcomes |
| `access/application/RoleAssignmentService` | `replaceRolesOfUser` and `assignInitialRoles` require the caller to hold the permissions of each role they add |
| `notes/application/NoteService` and its store | The list uses the access filter |
| `platform/web/GlobalExceptionHandler` | A refusal emits a decision record |
| `platform/security/SecurityConfiguration` | Startup fails if the request gate is absent while the identity module is present |
| `frontend/src/features/identity/AdminFrame.tsx`, `lib/api/problem.ts` | A guard per administration section; a message and a redirect for an expired session |

## 4. New functionality delivered

- A decision: allow or deny, a stable reason code, and the rule that decided.
- A typed request: principal, action, a resource reference built from the record, and a context map (empty for now).
- The combination rule: default deny; a deny decides; a module rule can only narrow; an error during evaluation denies and is logged as an error.
- Decision records in the structured log for every denial and for every allow of an action its module marks sensitive.
- The reason passed to `runAsSystem` appears in those records.
- The assignment rule: a user assigns only roles whose permissions they hold.
- One rule for invisible records, 404 or 403, recorded and applied to profiles and avatars (OQ-87).

## 5. Modules affected

`platform` (contract), `access` (decision point, role assignment), `identity`, `audit`, `files`, `notes` (callers; mechanical), and the frontend shell. No new module.

## 6. Dependencies

ADRs 0053, 0054 and 0055 accepted. No other phase. The engine measurement of draft [ADR 0056](../../decisions/0056-authorization-evaluation-engine.md) runs alongside, outside the main code, and does not block this phase.

## 7. Database changes and migration strategy

None. No table, column or row changes. `Owned` and `TenantBound` stay in this phase as the source from which a resource reference is built; they are the one temporary bridge, and milestone 8 removes them when resource types are declared.

## 8. Security implications

- Closes S1: the "only what you hold" rule now covers assignment as well as role editing.
- Closes S3: a module rule cannot widen what roles allow.
- Makes refusals visible for the first time (S5, S10 in part).
- Risk introduced: the decision point is rewritten. The control is that the existing tests are the specification: none may change its expected outcome except those for the assignment rule.

## 9. Automated tests required

- Every existing authorization test passes with changes only where it names a removed type: `AuthorizationIT`, `DefaultDecisionPointTest`, `AccessApiIT`, `TwoInstancesIT`, `IdentityGateIT`, `UserAdministrationIT`, `NotesApiIT`, `NoteAttachmentApiIT`, `AvatarApiIT`, `AuditViewerIT`, `FailedEventsApiIT`.
- New, in `AccessApiIT`: a holder of `access.roles.assign` cannot assign a role containing a permission they lack, for themselves or for another user, and can assign one they fully hold; the same through invitation.
- New, in `DefaultDecisionPointTest`: a widening rule has no effect; two narrowing rules both apply; an exception inside a rule denies; each reason code is produced.
- New: a refusal produces exactly one decision record with the expected fields and no attribute values.
- New: for each resource with a visibility rule, an existing ID and a non-existing ID answer identically to a caller without access.
- Frontend: a user with one administrative permission is refused the other sections' pages; an expired session leads to sign-in.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), the [policy decision point](../../shared/glossary/policy-decision-point.md) glossary entry, [architecture enforcement](../../architecture/architecture-enforcement.md), [how to add a feature](../../engineering/guides/add-a-feature.md), the [threat model](../../operations/security/threat-model.md) (the assignment rule), [API conventions](../../architecture/api/conventions.md) for the 404 rule, [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), and an implementation-decisions ADR for what was decided while building.

## 11. Acceptance criteria

1. No test in the list of section 9 changed an expected outcome, other than the assignment-rule tests.
2. The four new groups of tests pass.
3. `ModularityTest`, `ArchitectureTest` and `ArchitectureRulesFireTest` pass; rule 7 still fails the build for an unchecked use case.
4. The number of queries for one decision is measured and recorded as the baseline for later phases, and is not higher than before.
5. The `module-removal` job passes on all four rows.
6. No Flyway migration was added.
7. All of the above pass in CI.

## 12. Rollback and recovery

There is no data change, so rollback is reverting the commits of the phase. The assignment fix can be shipped alone, ahead of the rest, if the contract work is delayed: it touches one class and one test class.

## 13. Usable state at the end of the phase

Kibootia builds, starts and behaves as it does today for every user, with three visible differences: a role assignment that would exceed the assigner's own permissions is refused; a refusal appears in the log with a reason; and the administration screens refuse a section the user may not see instead of showing an error box. The database schema is unchanged.
