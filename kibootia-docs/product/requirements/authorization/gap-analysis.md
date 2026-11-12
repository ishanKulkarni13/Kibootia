# Authorization: Gap Analysis

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (analysis). Snapshot of commit `4cea34f` on 2026-11-11, from reading the code; nothing was run. Whether a requirement works today is in [traceability](traceability.md); this document says what it would take.

Part of the [authorization requirements](README.md). For each requirement: where it conflicts with the architecture as built, the change required, what it depends on, and its security and compatibility implications. "M7" to "M17" are the phases of the [authorization roadmap](../../roadmap/authorization-roadmap.md). Code names are under `backend/src/main/java/com/kibootia/backend/`.

## Reading the columns

- **Conflict** is with the code and the accepted ADRs, not with the idea. "None" means the architecture already has room.
- **Compatibility** is about stored data and the contract modules use. Kibootia has no forks and no users (owner, 2026-11-11), so a breaking change costs a migration and a test, not a deprecation period.
- A requirement that would add complexity for every application names the switch that keeps it optional (AUTH-040).

## Three structural conflicts behind most rows

Most gaps trace back to three shapes in today's code. They are stated once here and referred to by letter.

| | Shape today | Why it blocks |
|---|---|---|
| **A** | `PolicyDecisionPoint.can` returns a boolean and takes an untyped `Object` | No reason to record or explain; the decision point cannot ask a record for its scopes, relations or attributes |
| **B** | Reach is encoded in permission names: `x` means own records when `x-any` exists (`DefaultDecisionPoint`, `PermissionRegistry`), and `Scope` is `ALL`, `OWN`, `NONE` | Every further kind of reach (department, relation) would need another suffix on every action |
| **C** | `role_assignment` is (tenant, user, role) and the only subject is a user | No place for where, for how long, or for a group or service |

## Model, principals and decision rules

| ID | Conflict with today's architecture | Change required | Depends on | Security and compatibility |
|---|---|---|---|---|
| AUTH-001 | **B**. Modules declare permissions only (`PermissionProvider`); nothing declares resource types, attributes, relations or scope types | Declarations per module for resource types and action kinds (M8), scope types (M9), relation types (M10), attributes (M11), all validated at startup like permission names today | AUTH-038 | Startup validation is a security control: an undeclared name must stop the application, as it does now. Data: none |
| AUTH-002 | None in principle: roles are already runtime data. Everything else is code | Each later concept gains an administration API and screen within the bounds a developer declares; lifecycle in M15 | AUTH-001, 028, 030 | The "only what you hold" rule must cover every new thing an administrator can attach (reach, scope, condition), or configuration becomes an escalation path |
| AUTH-003 | **A**, **B**. One evaluation path that knows only permission and owner | One model, grants with a reach and an optional named condition ([ADR 0054](../../../decisions/0054-authorization-domain-model.md)), so the four models are four limits on one grant, not four subsystems | AUTH-001, 004, 005, 006 | One evaluation path means one place to test and one explanation. Risk: the single path becomes complex; mitigated by building it phase by phase with the scenario tests |
| AUTH-018 | **C**. `Caller` has no type; `UserTokenValidator` refuses machine tokens (ADR 0044) | A typed principal (M7); service principals (M12); groups as assignment subjects (M13) | AUTH-035, 019, 039 | Service principals touch token validation, the most sensitive code. Group nesting needs cycle refusal under a lock |
| AUTH-019 | No group concept at all | Group tables, membership with a source per group (manual, rule, synchronized), a job for the two automatic sources | AUTH-018; the identity-provider port for synchronization | Synchronized membership means an external system can grant access; the source must be recorded and a group must have exactly one source (OQ-55) |
| AUTH-020 | First-match `ResourcePolicy` can widen; no deny rule exists | A combination rule in the contract: default deny, deny overrides, a module rule can only narrow, an evaluation error denies (M7); deny grants as an optional capability (M8) | AUTH-034 | Closes security findings S3 and S4. Behavior today is unchanged because no production policy exists |

## Assignments, relationships and scopes

| ID | Conflict with today's architecture | Change required | Depends on | Security and compatibility |
|---|---|---|---|---|
| AUTH-004 | **C** | Scope nodes; assignment rows gain a scope node, start, expiry, suspension and a surrogate key; existing rows move to the tenant root | AUTH-007, 021 | Migration must leave every user's access identical (equivalence test). An assignment at one node must never reach a sibling node: tested per scenario |
| AUTH-005 | **A**, **B**. The only relation is `Owned.ownerId` | Declared relation types; a relationship table; grants that reach "by relation"; the owner relation derived from the record's own column so it is not stored twice | AUTH-001, 009 | A relationship record is a grant in disguise: writing one must be authorized and audited like an assignment |
| AUTH-007 | `Scope` has no hierarchy; a record has no scope | Scope types with a parent order; a record declares its **set** of scope nodes; list filters join against that set | AUTH-004, 024 | Hierarchy queries on every decision: cost must be measured. A wrong ancestor table silently widens access, so the table is rebuilt and compared in a test |
| AUTH-008 | Roles combine, but all tenant-wide | Follows from AUTH-004: several assignments at different nodes, evaluated per record | AUTH-004 | "Highest role wins" must not reappear: a test holds one user as administrator of one department and plain member of another |
| AUTH-009 | No per-record relation except owner | Follows from AUTH-005 plus optional validity on a relationship record | AUTH-005, 021 | Expiry checked at decision time, not by a job, so an expired relation can never authorize |
| AUTH-016 | None beyond AUTH-005 and AUTH-007 | A record in several scope nodes and several relations at once, both already needed by the reference applications | AUTH-005, 007 | Open requirement (OQ-50): the evidence from Timetia and indent-edi supports confirming it |
| AUTH-017 | ADR 0028 excludes live tenant resolution and tenant management; jobs name the default tenant in code | A tenant table, a `TenantResolver` implementation, jobs iterating tenants, tenant and platform administrators | AUTH-028 | The highest-impact isolation risk. Needs a guard for native SQL without a tenant (finding S7) and tests with two live tenants. Must stay off for single-tenant applications |
| AUTH-024 | No inheritance concept | Per scope type: whether an assignment reaches child nodes; per action: an opt-out; an explanation of where a permission came from | AUTH-007 | Inheritance is where access spreads unnoticed; default is no inheritance unless declared |
| AUTH-025 | A role is already a bundle; a second layer does not exist | A bundle table and role-to-bundle links, with a preview of affected roles and principals before a change | AUTH-030 | A bundle edit changes many roles at once; it goes through the definition lifecycle. Optional (switch) |
| AUTH-046 | One fixed meaning of ownership | Owner, creator and assignee as separate declared relations | AUTH-005 | Proposed. Ownership granting everything by default would be an escalation; it grants only what a role's grant by that relation says |
| AUTH-047 | No transfer operation anywhere | A transfer use case per resource type, its own action, an audit entry, and the scope set updated in the same transaction | AUTH-005, 007 | Proposed. A transfer is a privilege change for everyone in the old and new scope: both sides must hold the right, and the change must be atomic |

## Conditions and resource access

| ID | Conflict with today's architecture | Change required | Depends on | Security and compatibility |
|---|---|---|---|---|
| AUTH-006 | **A**. Three built-in facts only | Declared attributes per resource type; named conditions in Java attached to grants; a database form for conditions used on lists | AUTH-001 | Attributes must come from the server's own record, never from the request, or a caller can lie their way past a condition |
| AUTH-010 | None: already one permission per action | Separate "action" (what a use case asks) from "grant" (what a role gives), and declare each action as collection or instance | AUTH-001 | Closes the trap where a method-level check passes with own-records reach |
| AUTH-011 | None in principle; nothing uses it | The use case passes workflow facts in the request context; named conditions test them | AUTH-006 | The application stays the owner of states and transitions; Kibootia never stores workflow state |
| AUTH-014 | Response mappers return every field; no per-field decision | Declared sensitive field groups, each with its own action; a helper that mappers and update handlers call | AUTH-006, 010 | Field-level rules are easy to forget on one endpoint (export, search). A test per resource type lists every response type that carries a protected field |
| AUTH-015 | Same as AUTH-011 | Same mechanism | AUTH-006 | Same |
| AUTH-043 | Same as AUTH-011 | Same mechanism; revision and approval status are attributes | AUTH-006 | Proposed. No new machinery |
| AUTH-048 | No bulk operation exists | One call that decides for many records and returns a decision each; the use case chooses all-or-nothing or skip | AUTH-051 | Decisions must be made at execution, not at selection (finding S2) |
| AUTH-049 | No export or print feature exists | Export and print as ordinary actions with their own grants and their own list filter | AUTH-014, 051 | Proposed. An export that reuses the view filter leaks whatever view allows; it needs its own |
| AUTH-051 | **B**. Three-value scope used by one module | An access filter (scope nodes, relations, named conditions) that each store turns into its query | AUTH-004, 005, 006 | The requirement that decides the engine ([ADR 0056](../../../decisions/0056-authorization-evaluation-engine.md)). Counts and "filter options" endpoints leak too and must use the same filter |

## Delegation, time limits, duties and approval

| ID | Conflict with today's architecture | Change required | Depends on | Security and compatibility |
|---|---|---|---|---|
| AUTH-012 | `AuditLog` records the acting user only, by design (ADR 0046) | A delegation record; evaluation against the delegator's current authority; a second identity column in audit entries and decision records | AUTH-004, 021; an ADR extending 0046 | Delegation abuse (finding S9): never more than the delegator holds now, no re-delegation by default, never the power to assign or delegate |
| AUTH-013 | None for eligibility; counting approvals would break the responsibility boundary | Eligibility through relations and scope; an optional "not the author" rule. Counting stays in the application | AUTH-005, 026 | Self-approval restriction must be off by default (Timetia forbids forcing a review) |
| AUTH-021 | **C** | Start and expiry on assignments and relationship records, evaluated with the platform clock in UTC | AUTH-004 | An expiry enforced by a job would leave a window; enforce at decision time |
| AUTH-026 | Only fixed safeguards exist | Static rules checked when an assignment or membership is written; dynamic rules as a grant exclusion ("not when the principal has relation R to the record") | AUTH-004, 005, 018 | Static rules must also hold through groups and delegation, or they are bypassed |
| AUTH-041 | Not applicable | Scoped exceptions to a rule, as data | AUTH-026 | Proposed. An exception is a hole by design: scoped, audited, never global |
| AUTH-042 | Not applicable | No new machinery beyond AUTH-013 | AUTH-005, 013 | Proposed |
| AUTH-045 | None | Documentation and one test: a business deadline is an attribute, an authorization expiry is a field of an assignment | AUTH-021 | Proposed. Confirmed by Timetia's ticket expiry |

## Administration, audit and governance

| ID | Conflict with today's architecture | Change required | Depends on | Security and compatibility |
|---|---|---|---|---|
| AUTH-022 | **A**. The audit table is written inside business transactions and sized for changes (ADR 0046) | A decision with a reason code (M7); denial records in the structured log (M7); a stored decision log with a viewer and retention (M15) | AUTH-034; an ADR extending 0046 | Records carry no attribute values. High volume: default is denials and sensitive allows only |
| AUTH-023 | Not applicable | Deferred | AUTH-030 | Deferred |
| AUTH-027 | Screens exist for users, roles and the audit log only | A screen with each phase that adds an administrable thing; unified in M15 | Every functional requirement | The screen is never the control; each endpoint keeps allowed and denied tests |
| AUTH-028 | One implicit all-powerful administrator role (`StoredPermissionSource`); assignment does not check what the assigner holds | The assignment rule (M7); the administrator as an explicit role (M8); administration actions granted at scope nodes (M15); tenant and platform levels (M16) | AUTH-004, 017 | Closes finding S1. The last-administrator safeguard must be redefined per scope when scoped administrators exist |
| AUTH-029 | No pending-change concept | One fixed pattern: a change waits for a configured number of approvers other than the requester | AUTH-030 | The approver must hold the right to make the change; self-approval of one's own change is refused |
| AUTH-030 | Roles are edited in place with an optimistic version | Versioned definitions with draft, validation, publication and rollback; operational data stays immediate | AUTH-025 | Rollback must never restore an assignment (review note on AUTH-030) |
| AUTH-044 | One log-wide permission | A per-record history action decided through the decision point | AUTH-005, 022 | Proposed. Reading history is itself recorded |
| AUTH-050 | None | Every new administrable thing writes its audit entry in the transaction of the change, as roles do | Each phase | A phase is not complete while a change it introduces is unaudited |

## Integration and enforcement

| ID | Conflict with today's architecture | Change required | Depends on | Security and compatibility |
|---|---|---|---|---|
| AUTH-031 | None | Keep; add the companion startup check for action kinds (M8) | AUTH-010 | The coverage rule still misses a check inside a lambda |
| AUTH-032 | ADR 0044 and ADR 0050 decided against a cache | Only if a measured target is missed; then a cache validated against a version number | AUTH-033; targets (OQ-70 to OQ-73) | A cache is the main source of stale permissions; revocation wins |
| AUTH-033 | None: met today | Keep true in every phase; `TwoInstancesIT` extended for each new kind of grant | Every phase | Per-transaction snapshot for long operations (finding S2) |
| AUTH-034 | **A** | Contract version 2 behind the same port and facade | None | One deliberate break; no shim |
| AUTH-035 | ADR 0044 refuses machine clients; the system caller is all-powerful | Registered service principals with grants; jobs and event handlers run as named ones | AUTH-018, 039 | Closes findings S5 and S6 |
| AUTH-036 | None | Scope and relation checks at the data boundary through the access filter | AUTH-051 | None |
| AUTH-037 | ADR 0023: nothing is published | The Java contract and the OpenAPI-described administration API are the "SDK" (owner decision: in-process module) | AUTH-034 | Changes listed in upgrade notes once releases exist |
| AUTH-038 | Permissions only | Follows from AUTH-001 | AUTH-001 | Same startup validation |
| AUTH-039 | Subject must be a UUID; Keycloak-shaped service-account test | One identity-owned mapping from a token to a principal; a stored external subject beside the internal ID | AUTH-018 | A profile-table migration; a wrong mapping is an identity confusion bug, so it is tested with a second token shape |
| AUTH-040 | The mechanism exists for modules, not for authorization capabilities | A property switch, a recipe and a CI row for each optional capability | ADR 0024 | A switched-off capability must fail closed: stored data for it is ignored, never half-applied |
