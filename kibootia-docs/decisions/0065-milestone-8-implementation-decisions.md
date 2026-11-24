# 0065 Milestone 8 implementation decisions

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** observed on branch `m-8` (local run, 2026-11-24; CI run pending). Recorded in [implementation status](../product/roadmap/implementation-status.md).

## Context

[Milestone 8](../product/roadmap/milestone-8-grant-model.md) replaces "a role is a list of permission names, where `-any` means every record" with the grant model of draft [ADR 0054](0054-authorization-domain-model.md), and point 5 of draft [ADR 0055](0055-authorization-decision-contract.md). It was built on the owner's instruction of 2026-11-24, while ADRs 0053 to 0055 still await acceptance. The milestone left several things open, and one of its steps could not be built as written.

## Decision

**The declarations.** In `platform.authorization`, a module declares `ResourceType`s (a name and the relations a record can carry, for example `notes.note` with `owner`) and actions with `Permission.onCollection(...)` or `Permission.onRecord(name, resourceType, ...)`. `forEveryUser()` offers an action to the built-in `user` role, with a reach (`forEveryUser(Reach.owner())` for "their own"). `Reach` is `all` or `relation:<name>`; `Grant` is an action, a reach and an effect (`ALLOW` or `DENY`). The registry refuses at startup an action on an undeclared type and a user-role reach the type does not offer.

**Which actions are on a record.** Only actions whose records have an owner are declared on a record today: viewing, changing and deleting a note. Every action of `identity`, `access`, `audit` and `files` is declared on the collection: they are administrative, their only reach is `all`, and they are rightly checked with `@RequiresPermission`. Declaring them on records would change nothing a role can express and would force every one of those use cases onto record checks.

**`@RequiresPermission` only on actions on the collection.** Startup fails when the annotation names an action on a record, naming the method. It caught one case on its first run: a test fixture that listed widgets with `@RequiresPermission(VIEW)`, which would have passed for a holder of their own widgets only.

**The administrator is an explicit role.** It holds an allow, reaching every record, for every declared action, written as rows and kept complete at every start, so an action a module adds later is granted to it automatically. It stays not editable. The rule "the administrator role means every permission", which was coded twice (finding C-5 of the assessment), is gone from the decision path.

**The conversion runs in Java at startup, not in the migration.** The milestone planned SQL in the `access` migration folder. Whether a stored `x` meant "my own records" depends on which actions are declared on a record type with an owner, which only the running application knows; and the only `-any` permissions belong to `notes`, which is removable, so naming them in an `access` migration would break the module-removal scan. The migration `V202611241000__access_role_grant.sql` creates `role_grant`, with a foreign key to `role` (the intra-module key of draft [ADR 0059](0059-data-integrity-in-the-database.md)). `BuiltInRoles` then converts every `role_permission` row by one rule: `x-any` becomes `x` reaching all; `x` becomes `x` reaching the owner when `x` is now an action on a record whose type has an `owner`, and `x` reaching all otherwise. A name no module declares is left in `role_permission`, so it is converted once its module is back. `role_permission` is dropped by a later migration, as the milestone planned. `LegacyGrantEquivalenceTest` compares every decision and every list filter of version 1 with the decision after conversion, for every subset of stored names (128) and every action and kind of record: all identical.

**Deny grants.** Switch `kibootia.access.deny-grants-enabled`, off by default. When off, a role cannot be given a new deny grant (400 `deny_grants_disabled`); deny grants that already exist are still applied, because ignoring them would widen access, and a role edit keeps them. A deny must reach every record (`all`): a deny of "only my own" would need a list filter that excludes, which stores cannot apply yet, so it is refused (400 `invalid_reach`) and, if stored, ignored rather than half-applied. A deny needs only that its editor holds the action.

**"Only what you hold" covers reach.** An editor or assigner can give a grant only if their own grants for that action reach at least as far: holding `notes.notes.view` for their own notes does not let anyone grant it for everyone's. Role editing and role assignment share one check (`Grantable`).

**The notation `action/reach`.** Grants are written `notes.notes.update/relation:owner` (with `!` for a deny) in audit entries and decision records. The first form, `action@reach`, was refused by the audit log, which rejects any value containing `@` so that it never holds an email address (ADR 0046); a unit test now checks that the short form passes the audit log's own validation.

**Hand-written SQL names the tenant (architecture rule 14, finding S7).** The milestone asked for "an architecture rule or startup check". ArchUnit sees classes, not SQL, so the rule is a test over the source text (`TenantSqlRules`): every string literal or text block that names a table with `tenant_id` must carry a `tenant_id` condition, or name the column for an insert; an open `where` is allowed only in a file whose condition builder starts with the tenant; a statement that deliberately spans every tenant carries the comment `// sql: every tenant` with a reason. The one such statement is the version-1 conversion. The rule fires on four made-up statements and, with the marker removed, on that one; SQL assembled from constants in another file is out of its reach.

**The API.** A role has `grants: [{action, reach, effect}]` instead of `permissions`. `GET /api/v1/permissions` returns `{permissions, denyGrantsEnabled}`, each action with its `resourceType`, the `reaches` a grant of it can have, the `userRoleReach` and `sensitive`. `GET /api/v1/me` adds `reaches`: for each held action, how far it reaches; `permissions` stays as the list of held actions. A malformed reach is a 400 with a field error, never a server error.

**The role editor** shows a reach selector beside each permission with more than one reach, offers only the reaches the editor holds, keeps a reach already in the role visible, and shows and keeps the role's deny grants without editing them.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Convert in the SQL migration, as planned | Needs declarations the migration cannot see, and would name a removable module's permissions in `access` |
| Keep the administrator implicit | The second copy of the rule stays, and a scoped administrator (milestone 15) would be a special case |
| Ignore stored deny grants while the switch is off | Switching a feature off would silently widen access |
| Deny grants with a relation reach | Lists would need a filter that excludes records; refused until stores can apply it |
| Declare every action on a record | No difference in what can be expressed, and every administrative use case would move off `@RequiresPermission` |

## Consequences

- No code path uses an `-any` permission any more. The name survives only in the conversion and its tests, which handle version 1 on purpose, and in comments and documents that say what the grant model replaced.
- `role_permission` stays until a later migration drops it; installations must start once on this version first.
- Frontend checks use `reachesAll(action)` where they used an `-any` name.
- The number of queries per decision is unchanged by reading: one query for the user's grants.

## Status

draft. Awaiting the owner, together with ADRs 0053 to 0055.

## Related

[Milestone 8](../product/roadmap/milestone-8-grant-model.md), [ADR 0054](0054-authorization-domain-model.md), [ADR 0055](0055-authorization-decision-contract.md), [ADR 0064](0064-milestone-7-implementation-decisions.md), [authorization](../architecture/identity/authorization.md).
