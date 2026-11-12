# Authorization Requirements

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (index). Per-requirement status is in [traceability](traceability.md).

The authoritative requirements for Kibootia's authorization and access control system. This folder **is** the specification; [summary.md](summary.md) is a one-page index into it and never competes with it. The specification was merged on 2026-11-11 from two drafts in `temp/` (`authz-new-srs.md`, the primary source, and the older `authz-srs.md`) and corrected during a review of the code and documents; the review is `temp/authorization-review.md`. The owner decided on 2026-11-11 that every Approved requirement is committed scope; that decision is recorded in draft [ADR 0053](../../../decisions/0053-adopt-authorization-requirements.md).

These documents say **what** must be supported. They choose no engine, policy language, schema or API; those decisions are in draft ADRs [0054](../../../decisions/0054-authorization-domain-model.md), [0055](../../../decisions/0055-authorization-decision-contract.md) and [0056](../../../decisions/0056-authorization-evaluation-engine.md). What exists today is described in [authorization](../../../architecture/identity/authorization.md).

## Documents

Read in this order.

| Document | Requirements |
|---|---|
| [summary.md](summary.md) | Every requirement in one line, with status, and the proposals that were rejected |
| [scope-and-principles.md](scope-and-principles.md) | Purpose, scope, responsibility boundary, terms, design principles |
| [model-and-principals.md](model-and-principals.md) | AUTH-001, 002, 003, 018, 019, 020 |
| [assignments-relationships-and-scopes.md](assignments-relationships-and-scopes.md) | AUTH-004, 005, 007, 008, 009, 016, 017, 024, 025, 046, 047 |
| [conditions-and-resource-access.md](conditions-and-resource-access.md) | AUTH-006, 010, 011, 014, 015, 043, 048, 049, 051 |
| [delegation-duties-and-approval.md](delegation-duties-and-approval.md) | AUTH-012, 013, 021, 026, 041, 042, 045 |
| [administration-audit-and-governance.md](administration-audit-and-governance.md) | AUTH-022, 023, 027, 028, 029, 030, 044, 050 |
| [integration-and-enforcement.md](integration-and-enforcement.md) | AUTH-031 to 040 |
| [non-functional.md](non-functional.md) | Quality requirements and acceptance criteria |
| [reference-scenarios.md](reference-scenarios.md) | Validation scenarios for Timetia and indent-edi |
| [traceability.md](traceability.md) | Each requirement against the code: supported, partly supported or unsupported, with evidence and the planned phase |
| [gap-analysis.md](gap-analysis.md) | Per requirement: conflicts with today's architecture, the change required, dependencies, security and compatibility implications |

## Requirement status

Each requirement has one status.

| Status | Meaning | Labels in the drafts |
|---|---|---|
| Approved | Agreed by the owner. Committed scope since 2026-11-11 | "Established", "Established through AUTH-014" |
| Proposed | Recommended to complete the set; not approved by the owner yet (OQ-80) | "Proposed refinement" |
| Open | Needs an explicit owner confirmation before design | "Intended direction; explicit confirmation required" |
| Deferred | Postponed beyond the initial implementation, by the requirement itself | "Future scope" |
| Rejected | Considered and refused, with the reason; kept so it is not proposed again | none in the drafts; see [summary.md](summary.md#rejected-proposals) |

Requirement status is not implementation status. Whether a requirement works today is recorded only in [traceability](traceability.md), using the [implementation vocabulary](../../../meta/living-documentation.md#implementation-status-vocabulary).

## Review notes

A paragraph that starts with **Review note** is a recommendation from the 2026-11-11 review, not an owner decision. It marks a place where the requirement as drafted is ambiguous, conflicts with an accepted ADR, or would pull business logic into the foundation. Each note is settled when the owner confirms or rejects it; until then the requirement text above it stands. A paragraph that starts with **Owner decision (2026-11-11)** records an answer the owner has given.

## Changing a requirement

- Identifiers are stable. A requirement keeps its number when a document is reorganised, and a removed number is never reused.
- A new requirement takes the next unused number (next: AUTH-052) and is added to [traceability](traceability.md) in the same change.
- A changed or removed requirement is noted in the document that holds it, with the date and the reason.
- When a consuming application shows a new need, decide first which of these it is: a generic authorization capability that belongs in Kibootia; an authorization definition of that application; a business rule of that application; a capability that should stay optional; or something for later. Only the first becomes a requirement here.

## What changed from the drafts

| Change | Reason |
|---|---|
| AUTH-032 and AUTH-033 restored from the older draft | The newer draft lists both in its planning section but never defines them |
| AUTH-050 given to administrative configuration auditing | It was an unnumbered section (16.3) of the newer draft and part of AUTH-022 in the older one |
| AUTH-051 given to collection-level authorization | It was an unnumbered section (11.3), "established through AUTH-014"; it is delivered and tested separately from field-level access |
| AUTH-045 placed with time-bound authorization | It was a chapter of its own |
| Status labels reduced to the five above | See the table |
| The 30 open questions of the newer draft moved to [open questions](../../../decisions/open-questions.md) as OQ-50 to OQ-73, except six that are indent-edi's own business questions (removed; the answers belong in its repository) | One home for undecided items |
| The scope classification (section 26 of the newer draft) replaced by the [authorization roadmap](../../roadmap/authorization-roadmap.md) | Delivery order belongs to the roadmap |
| The reference scenarios rewritten from the applications' own documents | The drafts described them from memory |
