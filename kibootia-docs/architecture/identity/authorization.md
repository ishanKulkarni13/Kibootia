# Authorization

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** stored roles, the editable role model, email verification and the safeguards are verified (CI run 42780048416, 2026-11-10). Contract version 2 (decisions with reasons, access filters, narrowing rules, decision records) is observed on branch `m-7` (verified, CI run 45354823982, 2026-11-24).

Decisions: [ADR 0009](../../decisions/0009-role-based-authorization-first.md), [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md), [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md), [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md).

This document describes authorization **as it is built today**. What it must become is in the [authorization requirements](../../product/requirements/authorization/README.md); how far the code is from them is in their [traceability](../../product/requirements/authorization/traceability.md); the contract below is draft [ADR 0055](../../decisions/0055-authorization-decision-contract.md), built on the owner's instruction of 2026-11-24 and awaiting acceptance; the grant model of draft [ADR 0054](../../decisions/0054-authorization-domain-model.md) is next.

## Principles (owner decisions)

- Authorization decides whether an authenticated principal may perform a specific operation on a specific resource.
- It is enforced in the backend. Frontend visibility checks are for usability and never a security control.
- A complete attribute-based engine is not an initial requirement, but the design leaves room for contextual rules.

## Model

- **Actions and resource types** are declared in code by each module (the grant model of [ADR 0054](../../decisions/0054-authorization-domain-model.md), built in [milestone 8](../../product/roadmap/milestone-8-grant-model.md), [ADR 0065](../../decisions/0065-milestone-8-implementation-decisions.md)). An action is named `<module>.<resource>.<action>` (for example `identity.users.invite`, `notes.notes.update`) and is either **on the collection** (create, an administrative action; checked with `@RequiresPermission`) or **on a record** of a declared resource type such as `notes.note`, whose relations (`owner`) say how far a grant of it can reach. Administrators see actions as permissions. Undeclared types, reaches a type cannot have, and `@RequiresPermission` on an action on a record stop the application at startup.
- **Roles** are sets of **grants** stored in Kibootia's database. A grant is an action, a **reach** (`all`: every record of the tenant; `relation:owner`: only the records the holder owns) and an effect (`ALLOW`, or `DENY` where deny grants are switched on). Administrators create and edit roles, choose the reach of each grant, and assign roles in the administration interface. Tokens carry identity only.
- **Deny grants** (`kibootia.access.deny-grants-enabled`, off by default) take an action away whatever other roles give. A deny always reaches every record. Switching them off stops new ones from being added and keeps applying existing ones, so it never widens access.
- **Built-in roles:** a protected administrator role, which holds every declared action for every record and is kept complete at startup, and a `user` role assigned automatically at first sign-in, offered each action a module flags for it with the reach the module declares (viewing, changing and deleting one's own notes). Administrators may change the `user` role's grants but cannot delete it. In the reference application it does not include `identity.profiles.view`.
- **Ownership:** actions on one's own profile and avatar are allowed to any active user by ownership rules, without a role.
- **Email verification:** until the email address is verified, only viewing one's own profile, resending verification and signing out are allowed. Other operations are denied with `platform.email_not_verified`. A module can mark a permission `beforeVerification()`; none does yet. An unverified user holds no permissions, and `GET /me` says so.
- **Safeguards:** the last administrator who can sign in cannot be removed, demoted, disabled or deleted; a role editor can only give a role grants they hold, at least as far as they reach, and whoever assigns a role must hold every grant it carries as far as it reaches (since branches `m-5-1` and `m-8`, [assessment](../architecture-assessment.md), S-1); only an administrator can give the administrator role; a role that is still held cannot be deleted; and every change to roles, permissions and assignments is written to the [audit log](../audit-log.md).
- **First administrator:** `kibootia.identity.bootstrap-admin-email` grants the role to the user whose token carries that address, verified, while no administrator exists, at their first sign-in or the moment they first verify it. It is audited. Locally it names the `demo-admin` user.

## Decision point

Every protected operation asks the [policy decision point](../../shared/glossary/policy-decision-point.md), through the `Authorization` facade in `platform.authorization`. Contract version 2 ([ADR 0055](../../decisions/0055-authorization-decision-contract.md), built in [milestone 7](../../product/roadmap/milestone-7-authorization-contract.md), [ADR 0064](../../decisions/0064-milestone-7-implementation-decisions.md)):

| Operation | Answers | Used for |
|---|---|---|
| `decide(principal, action, resource)` | A **decision**: allow or deny, a stable reason code, and the grant or rule that decided | Single records, and actions on the collection (no record) |
| `filter(principal, action)` | An **access filter**: nothing, everything in the tenant, or records to which the principal has a relation (today: records they own) | Lists; the module's store turns it into its query condition |

- The **principal** is a user (ID, tenant, email-verified) or the system (tenant and the reason it was entered).
- The **resource** is a `ResourceRef` the owning module builds from its record (type, ID, tenant, relations such as `owner`), so the decision point never reads another module's tables.
- **Reason codes** are part of the contract: `authorization.granted`, `.system`, `.no_grant`, `.out_of_reach`, `.other_tenant`, `.email_not_verified`, `.refused_by_rule`, `.denied_by_grant`, `.evaluation_error`, `.no_decision_point`.

**The combination rule.** Default deny. An undeclared action is a programming error and throws. The system may act in its own tenant. An unverified user holds only actions marked for it. The user's grants for the action are collected from all their roles; a deny grant decides; an allow reaches either every record of the tenant or the records to which the user has its relation. A record of another tenant is refused. Then every module rule (`ResourceRule`) that applies is consulted: a rule can only **narrow**, never allow what grants refused, and a rule that throws refuses as `evaluation_error`, logged at error level.

The decision point is a port, so an external engine can replace the Java evaluation, provided it returns a decision with a reason and an access filter ([ADR 0056](../../decisions/0056-authorization-evaluation-engine.md)).

## Where checks are written

On application use-case methods, not controllers, because use cases are also called by other modules, jobs and event handlers:

- `@RequiresPermission(...)` for an action on the collection (create, an administrative action);
- `authorization.require(action, record)` after loading a record, with the record implementing `Resource`;
- `authorization.requireVisible(action, store.find(id), notFound)` for the read that loads a record, which applies the rule for invisible records below;
- `authorization.requireFilter(action)` for a list, turning the filter into the query.

An ArchUnit rule requires every public use-case method to have a check or an explicit `@NoAuthorization("reason")`, and the check must be a direct call ([architecture enforcement](../architecture-enforcement.md)).

## Decision records

Every refusal, and every allowed use of a permission its module marks `asSensitive()` (erasing, purging, granting), is written as a structured log line on the logger `kibootia.authorization.decision`: principal kind and ID, tenant, action, resource type and ID, outcome, reason code, what decided, the system's reason, and the trace ID. Records hold identifiers only, never attribute values. Every decision is also counted in the metric `kibootia.authorization.decisions`, tagged with outcome, reason and action. How long records are kept beyond the log is the decision log of milestone 15; they are not written to the audit table.

## Freshness

There is no permission cache at first. Each API request reads the caller's profile (the request gate, in `identity`) and each decision reads their permissions (`StoredPermissionSource`, in `access`), two indexed queries because each module owns its tables. Role changes and disabling therefore apply on the next request, on any number of instances; a test starts a second application context on the same database to show it. If measurement shows a need, the documented upgrade is an in-memory cache checked against a per-tenant authorization version number.

## Machine clients

Only tokens that belong to a user are accepted. Tokens issued to service accounts are rejected by default; service principals with roles are a future recipe.

## Failure behavior

An authenticated caller who is refused receives 403, distinct from 401 for authentication failures; an unverified caller receives 403 `platform.email_not_verified`. For records ([ADR 0064](../../decisions/0064-milestone-7-implementation-decisions.md), resolving OQ-87): a caller who holds **no grant** for the action gets 403, decided before any record is read; a caller who holds the action but whose grants do not reach the record, and a record that does not exist, both get 404 ([API conventions](../api/conventions.md)). The answer therefore never depends on whether an ID exists.

The request gate is required: the application refuses to start without it, rather than letting disabled or deleted users in.

## Testing

Every protected operation has an allowed and a denied test, and list endpoints test their scope: `AccessApiIT` for roles and assignments, `UserAdministrationIT` and `IdentityGateIT` for users, `AuditViewerIT` for the log, `AuthorizationIT` and `DefaultDecisionPointTest` for the mechanism, including the combination rule, narrowing rules, decision records and the metric.
