# 0055 Authorization decision contract, version 2

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. The contract in the repository is version 1: `PolicyDecisionPoint.can(Caller, String, Object)` returning a boolean and `scopeFor` returning `ALL`, `OWN` or `NONE` (verified, CI run 42605511477).

## Context

[ADR 0027](0027-central-authorization-decision-point.md) and [ADR 0044](0044-authorization-enforcement-conventions.md) put every check through one port, `PolicyDecisionPoint`, reached by modules through the `Authorization` facade in `platform.authorization`. That structure is sound and stays. The shape of the questions and answers does not fit the [authorization requirements](../product/requirements/authorization/README.md):

| Today | Problem |
|---|---|
| The answer is a boolean | A decision cannot say why, so it cannot be audited (AUTH-022) or explained (AUTH-024) |
| The resource is an `Object` with optional marker interfaces (`Owned`, `TenantBound`) | The decision point cannot ask for a resource's type, scope, relations or attributes |
| `Caller` is user ID, tenant, email-verified flag and a system flag | No principal type, so a service cannot be a caller (AUTH-035) |
| `scopeFor` returns `ALL`, `OWN` or `NONE` | A list cannot be limited to a department or to related records (AUTH-051) |
| `ResourcePolicy`: the first policy that supports a record decides alone | No combination rule; a policy can widen access past the permission; no deny (AUTH-020) |
| `@RequiresPermission(X)` passes when the caller's scope for X is only `OWN` | A method-level check on an action that can be limited to own records says less than it appears to |
| `runAsSystem` gives every permission and logs its reason at DEBUG | System authority is neither narrow nor visible (AUTH-035) |

The contract is used by every module and will be used by every fork, so it is changed once, deliberately, with [ADR 0054](0054-authorization-domain-model.md) as its vocabulary.

## Decision

The port and its placement stay. The contract becomes:

**1. A typed request.** A check is made about a principal, an action, a resource reference and a context.

- The **principal** has a type (user or service), an ID and the tenant.
- The **resource reference** is supplied by the module that owns the record: its declared type, its ID, its tenant, its scope node, and a way for the decision point to read its relations and declared attributes. `Owned` and `TenantBound` are replaced by it.
- The **context** holds request-time attributes the application declares (for example a workflow state passed in by the use case).

**2. A decision, not a boolean.** The decision point returns allow or deny together with a stable reason code and the grant or rule that decided. `Authorization.can` and `Authorization.require` keep their signatures for callers and are built on the decision; code that needs the reason asks for the decision.

**3. One combination rule.** Default deny. All applicable grants are evaluated. Any applicable deny decides. A module-supplied rule can only narrow what grants allow, never widen it. An error or missing required data during evaluation is a deny, and is logged as an error, not as an ordinary refusal.

**4. Lists get a filter, not a label.** `scopeFor` is replaced by a question that returns an **access filter**: nothing, everything in the tenant, or a set of alternatives of three kinds: "resources under these scope nodes", "resources to which the principal has this relation", and "resources matching this named condition" (in the database form the condition supplies, [ADR 0054](0054-authorization-domain-model.md)). The module's store turns the filter into its query. The rule of ADR 0044 stays: filtering happens in the database, so paging and totals are correct.

**5. Two kinds of action.** An action is declared as applying to a **collection** (create, list) or to an **instance** (view, update, delete, approve). `@RequiresPermission` is valid only for collection actions; an instance action is checked with the record. Startup validation refuses the annotation on an instance action, as it already refuses an undeclared permission name.

**6. Decisions are observable.** Every denial, and every allow of an action its module marks as sensitive, is emitted as a decision record: principal, action, resource reference, outcome, reason code, trace ID, time. Where the records are stored and how long they are kept is a later ADR (decision log). They are not written to the transactional audit table of [ADR 0046](0046-audit-log-design.md).

**7. System authority is named.** Work that no user started runs as a declared service principal with declared grants. `runAsSystem` stays as the way to enter that identity, its reason is recorded in the decision record, and the all-powerful system caller is removed once the foundation's own jobs and event handlers have their service principals.

**What stays exactly as it is:** the port lives in `platform`, the implementation in `access`; checks are written on use-case methods and ArchUnit rule 7 fails the build without one; a record the caller may not see answers 404 and one they may see but not change answers 403 ([ADR 0043](0043-http-api-conventions.md)); with no decision point everything is denied; no decision cache.

**Migration rule.** Version 2 is introduced with the six existing modules moved onto it and no change in observable behavior, except the role-assignment fix of ADR 0054 (rule 7): every existing allowed, denied and list-scope test passes unchanged except where it names a removed type.

**Delivery.** The contract arrives in three phases so that no phase changes the interface, the stored permissions and token validation together: points 1 to 4 and 6 in [milestone 7](../product/roadmap/milestone-7-authorization-contract.md), with no schema change; point 5 with the grant model in milestone 8; point 7 with service principals in milestone 12. Until milestone 12 the system caller stays, with its reason recorded in the decision record.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Keep version 1 and add overloads as features arrive | Each feature would bend the contract again; forks would merge a breaking change with every release |
| Expose the chosen engine's API to modules | Breaks engine independence (AUTH-034) and puts engine types in every module |
| Return the full set of permitted IDs for a list instead of a filter | Does not page, does not scale, and is the "load and filter in memory" alternative ADR 0044 already rejected |
| Let module rules both widen and narrow | A rule that widens access bypasses roles, so the role editor and the audit trail no longer describe what a user can do |
| Record every decision in the audit table | Decision volume is far above change volume; the audit table is written inside business transactions |
| Keep the all-powerful system caller | Cannot satisfy narrowly scoped service permissions (AUTH-035) |

## Consequences

- Every module changes once: resource references in place of marker interfaces, and a filter in place of the three-value scope in list queries. Kibootia has no forks or users yet (owner, 2026-11-11), so the change is made directly, without a compatibility layer, and before the first release tag.
- The decision point needs resource data it did not need before. Modules supply it through the resource reference, so the decision point never queries another module's tables.
- Reason codes become part of the contract and must stay stable, like the problem codes of the HTTP API.
- ArchUnit rule 7 gains a companion startup check (point 5), which closes the gap where a method-level check passes with own-records reach. The rule's known blind spot, a check inside a lambda, remains and is addressed separately.
- An external engine that replaces the Java evaluation must produce a decision with a reason and an access filter, not only yes or no. This is a hard requirement on the engine choice ([ADR 0056](0056-authorization-evaluation-engine.md)).

## Status

draft. A recommendation of the 2026-11-11 review, awaiting the owner's confirmation (OQ-83). On acceptance it replaces the contract shape in [ADR 0027](0027-central-authorization-decision-point.md) and the list-scope and machine-client paragraphs of [ADR 0044](0044-authorization-enforcement-conventions.md); both stay accepted otherwise.

## Related

- [ADR 0053](0053-adopt-authorization-requirements.md), [ADR 0054](0054-authorization-domain-model.md), [ADR 0056](0056-authorization-evaluation-engine.md).
- [Milestone 7](../product/roadmap/milestone-7-authorization-contract.md), [milestone 8](../product/roadmap/milestone-8-grant-model.md) and [milestone 12](../product/roadmap/milestone-12-service-principals.md), which implement this contract.
- [Security review](../temp/authorization-security-review.md), findings S3 to S5.
- [Architecture enforcement](../architecture/architecture-enforcement.md).
