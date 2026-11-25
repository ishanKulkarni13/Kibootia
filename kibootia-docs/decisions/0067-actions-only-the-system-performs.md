# 0067 Actions only the system performs

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** observed on branch `feat/admin-gaps` (`SystemOnlyPermissionTest`, `GrantsApiIT`; local run 2026-11-25; CI run pending). Recorded in [implementation status](../product/roadmap/implementation-status.md).

## Context

Every check in Kibootia is an action that a role can hold. That includes the purges that the retention job runs on its own: `audit.entries.purge`, `files.files.purge`, `files.files.reconcile`, `notes.notes.purge`, and, since milestone 5.1, `audit.failed-events.purge`. The job runs as the system, which is allowed everything, so none of these is ever checked against a role. They were nonetheless declared like any permission, so the role editor offered them, the administrator role held them, and giving one to a person did nothing: no endpoint calls them. Two more jobs borrowed a human permission instead (`access.roles.manage` for the role purge, `identity.users.erase` for the user purge), which made that permission look as though it also described what a job does.

Found by reading the code for gaps on 2026-11-25. Decided on the owner's delegation of that day.

## Decision

An action can be declared **for the system only** (`Permission.forSystemOnly()`).

1. The code still checks it, with `@RequiresPermission` or `Authorization.require`, like any action, so it appears in the declarations, in the decision record and in the architecture rules.
2. No role can hold it. It is left out of the catalogue the role editor reads (`PermissionRegistry.grantable()`), a grant of it is refused with `unknown_permission` and a message that says why, a stored grant of it is ignored, and the built-in administrator role does not hold it.
3. It cannot be offered to the built-in `user` role or used before the email address is verified: the declaration refuses that combination.
4. The system is allowed it, as it is allowed everything, so the jobs are unchanged.
5. Each job has an action of its own. The role purge uses `access.roles.purge` and the user purge `identity.users.purge`, both for the system only, so `access.roles.manage` and `identity.users.erase` mean what they say.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Leave them in the editor | A permission that does nothing when granted invites a mistaken belief that giving it matters, and an auditor reading the role sees authority nobody has |
| Do not declare them and let the job skip the check | The check and its decision record are how a purge is made reviewable; an undeclared action is refused at startup by design |
| Remove the checks from the purge methods | `@RequiresPermission` on every public use-case method is architecture rule 7; an exemption per purge would weaken it |
| A flag on the stored role instead of the declaration | The fact is about the action, not about a role, and belongs where the action is declared |

## Consequences

- The role editor and the catalogue list only what a role can be given. The administrator role is still complete: it holds every action a role can be given.
- A deployment that already stored grants of these actions (the administrator role was kept complete at startup) keeps the rows, and they are ignored. Nothing needs migrating.
- A new retention job declares its purge for the system only and does not borrow a human permission. The guide to adding a feature says so.

## Status

accepted on the owner's delegation of 2026-11-25.

## Related

- [ADR 0044](0044-authorization-enforcement-conventions.md), [ADR 0054](0054-authorization-domain-model.md), [ADR 0055](0055-authorization-decision-contract.md), [ADR 0034](0034-soft-delete-with-retention-purge.md), [ADR 0059](0059-data-integrity-in-the-database.md).
- [Add a feature](../engineering/guides/add-a-feature.md).
