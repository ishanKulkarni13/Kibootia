# Roadmap

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (index).

The project is built through seven ordered milestones, each a vertical slice: every capability is tested as soon as it exists, and the documentation is updated within the milestone. A milestone is complete only when its criteria are verified. The order and scope were set by the owner on 2026-10-28; the completion criteria were rewritten on 2026-11-08 after the second decision round and approved by the owner the same day (OQ-49). The vision is not limited to this order: delivery order and product scope are separate.

| Item | Summary |
|---|---|
| [implementation-status.md](implementation-status.md) | Per capability: intended, observed in the repository, verified |
| [milestone-0-hygiene-and-guardrails.md](milestone-0-hygiene-and-guardrails.md) | Clean-up, Java 25, externalised configuration, module skeleton with Spring Modulith and ArchUnit, CI |
| [milestone-1-walking-skeleton.md](milestone-1-walking-skeleton.md) | Sign-in through Next.js to a protected Spring endpoint, with traces, on a fresh clone; the `apps` profile and first Dockerfiles |
| [milestone-2-platform-conventions.md](milestone-2-platform-conventions.md) | Persistence, tenancy, soft delete, API behavior, outbox and jobs, the decision point, the audit module |
| [milestone-3-identity-and-access-administration.md](milestone-3-identity-and-access-administration.md) | Profiles, roles and permissions, admin UI, invitations and self-registration, audit log viewer, rate limiting |
| [milestone-4-files.md](milestone-4-files.md) | Object storage and the avatar flow |
| [milestone-5-example-feature.md](milestone-5-example-feature.md) | The deletable example feature (Notes, with a picture and screens), removal recipes and the module-removal matrix |
| [milestone-6-production-readiness.md](milestone-6-production-readiness.md) | Reference deployment, production guards, first recipes, first release |
| [authorization-roadmap.md](authorization-roadmap.md) | The slices (milestones 7 to 15) that deliver the [authorization requirements](../requirements/authorization/README.md). Draft, awaiting the owner's confirmation |
| [milestone-7-authorization-contract.md](milestone-7-authorization-contract.md) | The first authorization slice: the decision contract and domain model, with behavior unchanged. Draft |

Milestones 0 to 6 are the plan the owner set on 2026-10-28. Milestones 7 onward were proposed by the authorization review of 2026-11-11 and depend on draft [ADR 0053](../../decisions/0053-adopt-authorization-requirements.md). Whether milestone 7 comes before milestone 6 is undecided (OQ-85 in [open questions](../../decisions/open-questions.md)).
