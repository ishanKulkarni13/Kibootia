# 0060 Implementation status in one place

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** observed. The ADR change in item 1 is applied on branch `m-5-1` (52 ADRs). The rest is planned.

## Context

The documentation standards say that the `Implementation:` line is about the repository and that [implementation status](../product/roadmap/implementation-status.md) is the one place that holds the per-capability picture. They also say that ADRs "record a decision and its reasoning at a point in time; they do not describe the current state of the code" ([standards, section 7](../meta/documentation-standards.md)). In practice every ADR carried a banner restating status, often with a CI run ID, and each milestone made dozens of them wrong: 39 of the 52 accepted or deprecated ADRs opened with `planned` or `observed`, 15 specs, guides and recipes said "planned" or "no CI run yet" for things that the CI record shows were built and verified, and the architecture overview still said "nothing in this picture except the backend skeleton and MySQL exists" (P-1, P-2 of the [assessment](../architecture/architecture-assessment.md)). A fact kept in many places is wrong in most of them. The standards' first principle is one canonical home per fact.

## Decision

1. **An accepted or deprecated ADR carries one fixed line**: "not applicable (a decision record). What exists, and the CI run that verified it, is in implementation status", linking to it. Applied to ADRs 0001 to 0052 on branch `m-5-1`. A draft ADR may say `planned` and describe today's state while it is a proposal; it takes the fixed line when accepted.
2. **A spec's banner states a level and links to its row**, not a run ID. The run ID lives only in implementation status, so a milestone's CI result is recorded once. A spec that the code does not yet match says what is missing in one sentence. (The banners corrected on branch `m-5-1` still name the run, because the current standard asks for it; they change when this ADR is accepted.)
3. **Implementation details that lived only in ADR banners move into the spec they belong to.** Done for the BFF session (`architecture/identity/authentication.md`); each spec that depended on a banner is checked the same way.
4. **A documentation check runs in CI**: a script that fails on a broken relative link, a document without the standard header, a folder `README.md` that does not list its children, and an ADR whose row in `INDEX.md` disagrees with its status.
5. **Module documentation is generated where it can be.** Spring Modulith's `Documenter` writes the module diagram and canvases from the code; the [module catalogue](../architecture/module-catalogue.md) keeps what the generator cannot know (purpose, removability, events).
6. **The standards are amended** once this ADR is accepted: section 5 (the banner of an ADR and of a spec) and section 7 (the banner is an allowed edit of an accepted ADR).

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Correct each banner now and keep the rule | Accurate for a week; the same drift returns after the next milestone |
| Remove the `Implementation:` line from all documents | Specs benefit from saying how far they are true, and the vocabulary is useful |
| Generate every status from CI results | Worth doing later; a script that checks the links and headers is the smaller first step |

## Consequences

- Accepted ADRs lose their historical status notes from the working tree; git history keeps them.
- A milestone's evidence is recorded in one table instead of dozens of files.
- Until item 4 exists, review catches drift, as the standards already say.

## Status

draft. Awaiting the owner. Item 1 is applied on branch `m-5-1` because it removes text that is wrong; revert it with git if this ADR is rejected.

## Related

[Documentation standards](../meta/documentation-standards.md), [living documentation](../meta/living-documentation.md), [ADR 0022](0022-maintain-own-documentation-standards.md), [implementation status](../product/roadmap/implementation-status.md).
