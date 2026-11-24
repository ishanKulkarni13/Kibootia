# 0064 Milestone 7 implementation decisions

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** observed on branch `m-7` (local run, 2026-11-24; CI run pending). Recorded in [implementation status](../product/roadmap/implementation-status.md).

## Context

[Milestone 7](../product/roadmap/milestone-7-authorization-contract.md) builds points 1 to 4 and 6 of the decision contract of draft [ADR 0055](0055-authorization-decision-contract.md). The owner asked on 2026-11-24 for the documented authorization work to be implemented end to end; ADRs 0053 to 0055 are still drafts awaiting acceptance (OQ-81 to OQ-83), so this ADR is a draft too and records what was decided while building, for the owner to confirm. Several choices were left open by ADR 0055 and the milestone, and one existing test changed its expected outcome.

## Decision

**The contract types.** In `platform.authorization`:

- `Principal` is a sealed interface with two kinds, `User` (ID, tenant, email-verified flag) and `System` (tenant and the required reason). Sealing makes the service principal of milestone 12 a compile error in every `switch` that does not handle it.
- `ResourceRef` is the type, ID, tenant and named relations of one record, built by the owning module; a record passed to a check implements `Resource`. `Owned` and `TenantBound` are removed in this milestone, not kept as a bridge until milestone 8: with no forks, keeping two ways to describe a record had no benefit.
- `Decision` is a `Reason` plus what decided (a permission name or a rule name). `Reason` is an enum of stable codes (`authorization.granted`, `authorization.no_grant`, `authorization.out_of_reach`, `authorization.other_tenant`, `authorization.email_not_verified`, `authorization.refused_by_rule`, `authorization.denied_by_grant`, `authorization.evaluation_error`, `authorization.system`, `authorization.no_decision_point`). Whether a decision allows follows from its reason, so the two cannot disagree.
- `AccessFilter` is sealed: `Nothing`, `Everything`, or `AnyOf` alternatives, of which the only kind today is `RelatedTo(relation, subject)`. A store that understands only ownership calls `ownerOnly()`, which **throws** on any shape it does not know, so a later alternative can never be silently widened by an old store.
- `ResourceRule` replaces `ResourcePolicy`. A rule names itself, says which resource type and action it applies to, and returns an objection or none. It is consulted only after grants allowed, all applicable rules are consulted in order until one objects, and a rule that throws refuses as `evaluation_error`, logged at error level. Rules apply to single-record checks, not to list filters (documented on the interface; named conditions with a database form, milestone 11, cover lists).
- An undeclared action still throws `IllegalStateException`: it is a programming error, not an evaluation failure, and turning it into a refusal would hide a typo behind a 403.

**One rule for invisible records (OQ-87, resolved).** A caller who holds **no grant at all** for the action gets **403**, and that is decided from grants alone, before any record is read. A caller who holds the action but whose grants do not reach the record, and a record that does not exist, both get **404**. The answer therefore never depends on whether an ID exists. `Authorization.requireVisible(action, record, notFound)` applies the rule, and the notes use cases use it. The rule needs no change to profiles and avatars: another user's avatar already answered 403 from the permission alone, before the user was looked up, and 404 for one that does not exist.

**One expected outcome changed.** An unverified caller holds no permission, so under the rule above every operation on a note by ID is now 403 `platform.email_not_verified`. Contract version 1 answered 404 for those, while the list and create already answered 403 `email_not_verified`. The two tests (`NotesApiIT.unverifiedCaller`, `NoteAttachmentApiIT.unverifiedCaller`) now assert the consistent answer for an existing and a non-existing ID. It is decided before the note is read, so it reveals nothing, and it tells the caller what to do (ADR 0041). This is the only change of outcome apart from the assignment rule of milestone 5.1.

**Decision records and the metric.** `DecisionRecorder` in `platform.authorization` writes a record for every refusal and for every allowed use of a permission marked `asSensitive()`. It uses the SLF4J key-value API on the logger `kibootia.authorization.decision`, so each field (`authorization.outcome`, `.reason`, `.action`, `.principal.kind`, `.principal.id`, `.tenant`, `.decided_by`, `.resource.type`, `.resource.id`, `.system_reason`) is a field of the ECS JSON line, with the trace ID. Every decision is also counted in `kibootia.authorization.decisions`, tagged with outcome, reason and action. Recording lives in the facade, not in the decision point, so an external engine behind the port gets the same records. A refused list (`requireFilter` with nothing) is recorded as a refusal of the action on the collection.

The permissions marked sensitive: `identity.users.delete`, `identity.users.erase`, `access.roles.manage`, `access.roles.assign`, `audit.entries.purge`, `audit.failed-events.retry`, `files.files.purge`, `files.files.reconcile`, `notes.notes.purge`. With them, the system's use of the purge and erase permissions in jobs and event handlers is visible with its reason (finding S5 in part).

**The request gate is required (S11).** `SecurityConfiguration` refuses to start without a `RequestGate` bean, instead of skipping the filter that refuses disabled and deleted users.

**Frontend (S13).** The administration frame refuses a section the user may not see, with a heading and the links to the sections they may see. Queries and saves that fail with 401 send the browser to sign-in once, returning to the same page; `bff.unauthenticated` has a message.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Keep `Owned` and `TenantBound` until milestone 8, as the milestone said | Two ways to describe a record for one milestone, with no fork to protect |
| A boolean `allowed` field beside the reason | Two fields that could disagree; the reason determines it |
| Record decisions inside the decision point | An external engine behind the port would have to repeat it |
| Record every allow | Every request makes several decisions; the volume would bury the refusals. Sensitive actions are recorded instead |
| 404 for every refusal of a record, as version 1 mostly did | A caller who holds no grant learns nothing they do not know, and the unverified caller is better told to verify |
| Turn an undeclared action into a refusal | Hides a typo as a permanent 403 |

## Consequences

- `Authorization.scopeFor` and `requireScope` are gone; lists use `filter` and `requireFilter`. The guide [add a feature](../engineering/guides/add-a-feature.md) shows the new calls.
- Rule 7 of [architecture enforcement](../architecture/architecture-enforcement.md) still counts only a direct call to `Authorization`, so a use case must call `requireVisible` itself rather than through a private helper.
- The number of queries per decision is unchanged by reading: one query for the caller's permissions per decision (`StoredPermissionSource`). It was not measured; the measured baseline asked for by the milestone is still open.

## Status

draft. Awaiting the owner, together with ADRs 0053 to 0055. Resolves OQ-87.

## Related

[Milestone 7](../product/roadmap/milestone-7-authorization-contract.md), [ADR 0055](0055-authorization-decision-contract.md), [authorization](../architecture/identity/authorization.md), [security review](../temp/authorization-security-review.md) (S3, S5, S8, S11, S13).
