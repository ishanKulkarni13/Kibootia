# 0053 Adopt the authorization requirements as committed scope

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. The requirements exist as documents; of 51, 4 are fully met by today's code, 21 partly and 25 not at all ([traceability](../product/requirements/authorization/traceability.md)).

## Context

[ADR 0009](0009-role-based-authorization-first.md) fixed role-based authorization first and said a complete attribute-based engine was not an initial requirement. [ADR 0027](0027-central-authorization-decision-point.md) added one decision point with Java policies and kept a policy engine as a recipe. The [goals and non-goals](../product/goals-and-non-goals.md) list a shipped policy engine, a tenant-management subsystem and machine-to-machine clients as non-goals. Milestones 2 and 3 built exactly that scope, and it is verified.

The owner has since written a much larger set of authorization requirements, driven by two intended consuming applications, Timetia and indent-edi. A review on 2026-11-11 compared the requirements with the code and documents and merged the two drafts into the permanent [authorization requirements](../product/requirements/authorization/README.md). The owner was asked how the requirements relate to the accepted decisions and chose **full commitment** over a staged target and over keeping the core small.

The two reference applications were then read ([reference scenarios](../product/requirements/authorization/reference-scenarios.md)). Timetia needs scoped assignments, relationships, named conditions and per-record audit visibility; indent-edi needs ownership, a department scope and a separate export permission. Neither needs groups, delegation, separation of duties, bundles, the configuration lifecycle or multi-tenancy. The owner was shown this and **reconfirmed full commitment** on 2026-11-11, adding that the reference applications validate the design and do not limit it, that no integration with either is planned, and that Kibootia has no forks and no users, so breaking changes are acceptable.

## Decision

Every authorization requirement with status **Approved** is committed scope of the Kibootia foundation. Requirements with status Proposed, Open or Future are not committed by this decision.

What follows:

- **ADR 0009 is superseded.** Authorization is no longer "role-based first, attribute-based not initially required". Role-based, attribute-based, relationship-based and scope-based authorization are all in scope.
- **Three non-goals are withdrawn:** a shipped policy engine, an organization-management interface with a tenant-management subsystem, and machine-to-machine API clients. Whether an external engine ships is decided in [ADR 0056](0056-authorization-evaluation-engine.md); withdrawing the non-goal only removes the prohibition.
- **Parts of three accepted ADRs will be replaced by later ADRs**, each written when its slice starts. Until then the accepted text stays in force:
  - [ADR 0027](0027-central-authorization-decision-point.md): the port and the central decision point stay; the shape of the contract is replaced by [ADR 0055](0055-authorization-decision-contract.md), and "OPA as a recipe" is revisited by ADR 0056.
  - [ADR 0044](0044-authorization-enforcement-conventions.md): checks on use cases and the architecture rule stay; the three-value scope, the rejection of machine clients and the permission-name convention for ownership are replaced.
  - [ADR 0028](0028-tenant-ready-schema.md): the tenant-ready schema stays; "no live tenant resolution, no tenant management" is replaced in the multi-tenancy slice.
- **Authorization stays an in-process module** of the application, reached through the Java contract in `platform.authorization`. It is not a network service. The contract is kept as plain data (a request, a decision, a filter description), so an HTTP front can be added later without redesign.
- **Delivery is in phases**, in the order of the [authorization roadmap](../product/roadmap/authorization-roadmap.md), and every phase ends in a state that builds, starts, migrates and passes its tests. Commitment to the scope is not a commitment to a date, and no phase starts before its ADRs are accepted.
- **Three rules protect the foundation while the scope grows:**
  1. Nothing of Timetia's or indent-edi's domain enters foundation code. Their scenarios live in test fixtures ([reference scenarios](../product/requirements/authorization/reference-scenarios.md)).
  2. Every capability beyond today's baseline (roles, permissions, ownership) has a switch and a CI row proving the application starts without it ([ADR 0024](0024-package-modules-with-infrastructure-toggles.md)).
  3. Behavior that is verified today stays verified through every slice: deny by default, checks on use cases, lists filtered in the database, and a revocation that applies on the next request.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Staged target: the requirements are a long-term baseline, a small first increment is committed, and each conflicting ADR is superseded only when its increment is scheduled | The architect's recommendation. The owner chose to commit the whole Established set so that design decisions are made against the full picture and not one increment at a time |
| Keep the core small: build only what Timetia and indent-edi concretely need, deliver the rest as recipes | The owner wants a reusable authorization foundation, not one fitted to two applications |
| Leave ADR 0009 and the non-goals as they are and treat the requirements as a wish list | Accepted documents would contradict the owner's stated direction |

## Consequences

- The committed authorization scope is larger than everything built so far: eleven phases against the six milestones that exist.
- Six of the eleven phases (service principals, groups, delegation and duties, governance, multi-tenancy, caching) have no scenario from either reference application. They are built from the requirement text alone, with scenario tests written for the purpose, and they come after the phases a reference application exercises.
- A policy engine, live tenancy and service principals each bring operating cost that every fork inherits unless it switches them off. Rule 2 above exists for that reason.
- No compatibility shims are written. With no forks and no users, the contract and the stored permissions change directly, each with a tested migration. The contract and grant-model phases (milestones 7 and 8) come before [milestone 6](../product/roadmap/milestone-6-production-readiness.md), so the first release tag carries the new contract ([ADR 0023](0023-template-fork-adoption-model.md)).
- On acceptance: ADR 0009 becomes `deprecated (superseded by 0053)`; the index, the [goals and non-goals](../product/goals-and-non-goals.md), the [vision](../product/vision.md) and the [technology catalogue](../technology/README.md) are updated in the same change.

## Status

draft. Records the owner's choice of 2026-11-11; the text awaits the owner's confirmation (OQ-81). On acceptance it supersedes [ADR 0009](0009-role-based-authorization-first.md).

## Related

- [Authorization requirements](../product/requirements/authorization/README.md), [authorization roadmap](../product/roadmap/authorization-roadmap.md).
- [ADR 0054](0054-authorization-domain-model.md), [ADR 0055](0055-authorization-decision-contract.md), [ADR 0056](0056-authorization-evaluation-engine.md).
- [Authorization](../architecture/identity/authorization.md) (what exists today).
