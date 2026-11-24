# Roadmap

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** not applicable (index).

The project is built through ordered milestones, each a vertical slice: every capability is tested as soon as it exists, and the documentation is updated within the milestone. A milestone is complete only when its criteria are verified. The order and scope were set by the owner on 2026-10-28; the completion criteria were rewritten on 2026-11-08 after the second decision round and approved by the owner the same day (OQ-49). The vision is not limited to this order: delivery order and product scope are separate.

| Item | Summary |
|---|---|
| [implementation-status.md](implementation-status.md) | Per capability: intended, observed in the repository, verified |
| [milestone-0-hygiene-and-guardrails.md](milestone-0-hygiene-and-guardrails.md) | Clean-up, Java 25, externalised configuration, module skeleton with Spring Modulith and ArchUnit, CI |
| [milestone-1-walking-skeleton.md](milestone-1-walking-skeleton.md) | Sign-in through Next.js to a protected Spring endpoint, with traces, on a fresh clone; the `apps` profile and first Dockerfiles |
| [milestone-2-platform-conventions.md](milestone-2-platform-conventions.md) | Persistence, tenancy, soft delete, API behavior, outbox and jobs, the decision point, the audit module |
| [milestone-3-identity-and-access-administration.md](milestone-3-identity-and-access-administration.md) | Profiles, roles and permissions, admin UI, invitations and self-registration, audit log viewer, rate limiting |
| [milestone-4-files.md](milestone-4-files.md) | Object storage and the avatar flow |
| [milestone-5-example-feature.md](milestone-5-example-feature.md) | The deletable example feature (Notes, with a picture and screens), removal recipes and the module-removal matrix |
| [milestone-5-1-architecture-hardening.md](milestone-5-1-architecture-hardening.md) | Close the structural, data and operational gaps of the [architecture assessment](../../architecture/architecture-assessment.md). Partly built |
| [milestone-6-production-readiness.md](milestone-6-production-readiness.md) | Reference deployment, production guards, first recipes, first release |
| [authorization-roadmap.md](authorization-roadmap.md) | The phases (milestones 7 to 17) that deliver the [authorization requirements](../requirements/authorization/README.md). Milestones 7 and 8 built |
| [milestone-7-authorization-contract.md](milestone-7-authorization-contract.md) | The decision contract, the role-assignment fix and denial records, with no schema change. Built |
| [milestone-8-grant-model.md](milestone-8-grant-model.md) | Declared resource types and actions; grants with a reach replace `-any`; deny grants behind a switch. Built |
| [milestone-9-scoped-assignments.md](milestone-9-scoped-assignments.md) | Scope nodes, assignments with validity, inheritance, scope list filters. Planned |
| [milestone-10-relationships-and-ownership.md](milestone-10-relationships-and-ownership.md) | Relationships beyond ownership; per-record audit history. Planned |
| [milestone-11-conditions-and-data-operations.md](milestone-11-conditions-and-data-operations.md) | Named conditions, field-level rules, bulk, export and print. Planned |
| [milestone-12-service-principals.md](milestone-12-service-principals.md) | Service principals and identity mapping. Planned |
| [milestone-13-groups.md](milestone-13-groups.md) | Groups, nesting and membership sources. Planned |
| [milestone-14-delegation-and-duties.md](milestone-14-delegation-and-duties.md) | Delegation, separation of duties, reviewer rules. Planned |
| [milestone-15-administration-and-governance.md](milestone-15-administration-and-governance.md) | Bundles, administrator hierarchy, definition lifecycle, approvals, decision log. Planned |
| [milestone-16-multi-tenancy.md](milestone-16-multi-tenancy.md) | Live multi-tenancy. Planned |
| [milestone-17-caching-and-guarantees.md](milestone-17-caching-and-guarantees.md) | Measured targets; a cache only if needed. Planned |

Milestones 0 to 6 are the plan the owner set on 2026-10-28. Milestones 7 to 17 were proposed by the authorization review of 2026-11-11 and rest on [ADR 0053](../../decisions/0053-adopt-authorization-requirements.md), accepted on 2026-11-25; their order is in the [authorization roadmap](authorization-roadmap.md#phases). Milestone 6 is built after milestone 5.1 and before milestone 9, so that the first release tag carries the new contract and stored model.
