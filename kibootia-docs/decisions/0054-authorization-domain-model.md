# 0054 Authorization domain model

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Today's model is permissions in code, roles in the database and a role assignment per user ([authorization](../architecture/identity/authorization.md)).

## Context

The [authorization requirements](../product/requirements/authorization/README.md) name four models (role-based, attribute-based, relationship-based, scope-based) and many features on top of them. Built as four mechanisms side by side, they would give four places to look when asking "why can this person do this". The model built in milestones 2 and 3 has three limits that block the requirements:

- **Scope is hidden inside permission names.** `notes.notes.update` means "own records" because a sibling `notes.notes.update-any` exists ([ADR 0049](0049-milestone-2-implementation-decisions.md)). A department scope would need a third name, a batch scope a fourth, and so on for every action.
- **An assignment is only "user holds role".** The `role_assignment` table has no place for where the role applies or for how long.
- **Action and permission are one string.** The thing a use case asks about and the thing a role grants cannot differ, so one grant cannot be limited in reach.

A single vocabulary is needed before the contract ([ADR 0055](0055-authorization-decision-contract.md)) and the engine ([ADR 0056](0056-authorization-evaluation-engine.md)) can be decided.

## Decision

Kibootia uses one model in which a **grant** is the only thing that allows access, and the four "models" are four ways of limiting a grant's reach.

### The terms

| Term | Definition |
|---|---|
| Principal | Who acts or holds authority: a **user**, a **service** (an automated identity), or a **group** (holds assignments, never acts itself) |
| Resource type | A kind of record a module protects, declared in code, for example `notes.note` |
| Action | What a use case asks to do to a resource type, declared in code and named `<module>.<resource>.<action>` as today |
| Scope node | A point in an application-defined hierarchy, for example department "Physics". The tenant is the root of every hierarchy. Scope types and their parent-child order are declared by the application. A resource lies in a **set** of scope nodes, not one: a lecture can belong to a division, a batch and a department at once |
| Relationship | A stored fact `(subject, relation, object)`: a principal or group has a named relation to a resource or a scope node, for example `(user 7, reviewer, ticket 42)`. Relation names are declared per resource type |
| Attribute | A named, typed fact about the principal, the resource or the request that the application declares and supplies at decision time, for example `status` of a ticket |
| Condition | A **named condition**: a test over declared attributes, written by a developer in Java and given a name and a description, for example "theory lectures only". It must hold for a grant to apply. Administrators attach named conditions to grants; they do not write them (owner decision, 2026-11-11) |
| Role | A named set of grants, edited at runtime as today |
| Grant | One entry of a role: an action, a **reach**, and optionally a condition. A grant may be a deny |
| Assignment | A role given to a principal **at a scope node**, with an optional start, an optional expiry, a suspended flag and its provenance |

**Reach** is what replaces the `-any` suffix. A grant reaches either:

- **everything in scope:** every resource of the action's type that has, among its scope nodes, one at or below the scope node of the assignment; or
- **resources by relation:** only resources to which the principal has the named relation, for example `owner` or `reviewer`.

### How the four models map onto it

| Model | In this vocabulary |
|---|---|
| Role-based | A role assigned at the tenant root, with "everything in scope" grants |
| Scope-based | The same role assigned at a lower scope node |
| Relationship-based | A grant with reach "by relation" |
| Attribute-based | A condition on a grant |

### Rules

1. Access exists only through a grant reached by an assignment that is currently valid. Hierarchies, group membership and relationships grant nothing by themselves (AUTH-007).
2. A group's assignments apply to its members, through nesting, with cycles refused when membership is written (AUTH-018).
3. Whether an assignment at a scope node reaches child nodes is declared per scope type by the application, and can be switched off per action (AUTH-024).
4. Ownership is the relation `owner`, declared by a resource type like any other relation. Creator, assignee and owner can be different relations (AUTH-046).
5. A deny grant overrides every allow (AUTH-020). Deny grants are an optional capability.
6. A tenant is an isolation boundary for data and is not a scope node an administrator can re-parent. A department is a scope node (AUTH-017).
7. A principal can assign a role, at a scope node, only if they hold every grant of that role at that node or above it. The same rule already limits what a role editor can put into a role; applying it to assignment closes the gap found in the 2026-11-11 security review (owner decision, 2026-11-11).
8. A named condition that is attached to a grant for a list action must also exist in a form the database can apply; a condition without one can be attached to instance actions only. This is checked when the condition is attached.
9. Existing data migrates without a change in behavior: a stored permission `x-any` becomes a grant of `x` with reach "everything in scope"; a stored permission `x` that has an `-any` sibling becomes a grant of `x` with reach "by relation `owner`"; every existing assignment becomes an assignment at the tenant root with no expiry.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Keep the `-any` suffix and add more suffixes per scope | The number of permission names multiplies with every scope type, and the scope stays invisible to tools |
| Separate subsystems for roles, relationships and attribute policies | Several sources of truth; "why is this allowed" has no single answer; conflict resolution between them becomes its own problem |
| Adopt one engine's native model as Kibootia's vocabulary (for example relationship tuples only, as in Zanzibar-style systems) | Ties every fork to that engine and contradicts the requirement to stay engine-independent (AUTH-034); roles, validity periods and approval are administration concepts no engine models |
| Permissions granted directly to users, without roles | Harder to administer and to audit; nothing in the requirements asks for it. A resource-specific relationship covers the one-off case |
| Conditions typed by administrators in an expression language | A new dependency, a new attack surface, and a rule every explanation must interpret. Rejected for now by the owner (2026-11-11); neither reference application needs it, and it can be added behind the same "condition" concept later |
| One scope node per resource | Both reference applications have records in several scopes (a shared course; an indent with lines from two departments) |

## Consequences

- One explanation path: assignment, role, grant, reach, condition. This is what makes "explain the origin of a permission" (AUTH-007, AUTH-024) answerable.
- The permission catalogue shrinks: every `-any` permission disappears, and the role editor gains a reach selector per grant.
- New stored data: scope nodes and the membership of resources in them, relationships, groups, and wider assignment rows. Each arrives in the phase that needs it; none is created ahead of use.
- Modules must declare resource types and relations in addition to actions. The startup validation that exists for permission names is extended to them.
- The built-in administrator role, which today holds every permission implicitly, becomes a role with "everything in scope" grants for every action at the tenant root. Whether it stays implicit is decided with the administrator hierarchy (AUTH-028).
- Glossary entries for the new terms are written when this ADR is accepted.

## Status

draft. A recommendation of the 2026-11-11 review, awaiting the owner's confirmation (OQ-82). On acceptance it replaces the ownership-by-name convention of [ADR 0049](0049-milestone-2-implementation-decisions.md) and the permission-name paragraph of [ADR 0044](0044-authorization-enforcement-conventions.md), which otherwise stay accepted.

## Related

- [ADR 0053](0053-adopt-authorization-requirements.md), [ADR 0055](0055-authorization-decision-contract.md), [ADR 0056](0056-authorization-evaluation-engine.md).
- [Scope and principles](../product/requirements/authorization/scope-and-principles.md#terms) (the terms as the requirements use them).
- [Authorization scope](../shared/glossary/authorization-scope.md) and [policy decision point](../shared/glossary/policy-decision-point.md) in the glossary.
