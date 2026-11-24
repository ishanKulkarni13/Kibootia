# 0057 Consumer-owned ports and declared module dependencies

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** planned, in part observed. Declaring `allowedDependencies` for every module is done on branch `m-5-1` (`ModularityTest` passes locally, 2026-11-24; CI run 45354022559). The ports below are not built.

## Context

The [architecture assessment](../architecture/architecture-assessment.md) found that the compile-time module graph is clean but that three calls go back up at run time through ports hosted in `platform` (C-1), and that `platform` hosts contracts that belong to features (C-2, E-1):

- `platform.directory.UserDirectory` is identity's lifecycle (`INVITED`, `ACTIVE`, `DISABLED`, `DELETED`) placed in `platform` so that `access` and `audit` can ask about users without depending on `identity`. Spring Modulith sees only an edge to `platform`, so the cycle `identity` → `access` → `identity` is invisible to the verification.
- `platform.ratelimit.RateLimitCategory` is a closed enum of feature buckets (invitations, verification resend, uploads, administration actions). A new feature that wants a bucket edits `platform`.
- `platform.config` names identity and files property keys.

A technical kernel should not change when a feature does. Until now, only `platform` declared `allowedDependencies`, so the graph between business modules was whatever the code happened to do.

## Decision

1. **Every module declares `allowedDependencies`.** Done on branch `m-5-1`; `platform :: *` stands for all named interfaces of `platform`. A new edge fails `ModularityTest` until someone writes it down, which makes the graph a reviewed artefact (architecture rule 12, [architecture enforcement](../architecture/architecture-enforcement.md)).
2. **The consumer owns the port.** A module that needs something from another module declares an interface in its own top-level package, and the provider implements it. This is the dependency-inversion rule that the hexagonal decision ([ADR 0003](0003-hexagonal-architecture-where-boundaries-matter.md)) already applies to infrastructure, applied between modules:
   - `access` declares what it needs to know about a user (does this user exist) and `identity` implements it.
   - `audit` declares an actor-name lookup and `identity` implements it.
   - `UserDirectory` and `UserSummary` leave `platform`.
3. **The last-administrator rule moves to `identity`.** "Another administrator who can still sign in exists" combines access data (who holds the administrator role) and identity data (who can sign in). It belongs to the module that owns the user lifecycle. `access` keeps the lock and exposes the locked list of holders inside the caller's transaction (`MANDATORY`); `identity` filters by user state. With that, `access` no longer asks `identity` anything about users except existence, and the runtime cycle for this rule is gone, not only renamed.
4. **Rate-limit buckets are declared by the modules that use them**, through a provider bean that `platform` collects, as `PermissionProvider` already works. Configuration is keyed by the bucket's name.
5. **`StoredFiles`, `FileCategory` and the related types are named as the shared storage SPI** in `platform.files`, not generalised ahead of need. `FileCategory` stays image-only until a second kind of file exists; the first non-image category is the moment to generalise it.
6. **`platform.config` stops naming other modules' keys.** Each module that has required settings contributes them through a small interface that `platform` collects.
7. **`identity` keeps orchestrating the lifecycle**, including telling `access` to remove an erased user's assignments. The alternative, `access` listening to `UserErased`, would add the edge `access` → `identity` beside the existing `identity` → `access` and create a module cycle.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Leave the ports in `platform` | The cycles stay invisible and `platform` keeps growing with each feature |
| Merge `access` into `identity` | Removes the cycle by removing the boundary; but `access` is kept separate so that any module can check permissions without depending on user administration ([layers and boundaries](../architecture/layers-and-boundaries.md)) |
| `access` keeps its own copy of user state, updated by events | The last-administrator safeguard needs the truth at the moment of the change; an eventually consistent copy could let the last administrator be removed |
| Introduce Maven modules to make cycles a compile error | Rejected already in [ADR 0024](0024-package-modules-with-infrastructure-toggles.md); declared dependencies and ArchUnit give the same signal for less cost |

## Consequences

- One new rule to maintain: a new module adds its `allowedDependencies`, and the [module catalogue](../architecture/module-catalogue.md) row.
- `UserDirectory` is removed and its three users change; the tests that use it change with them.
- The concurrency test for two administrators removing each other (`AccessApiIT`) must keep passing; it is the check that the move in item 3 did not weaken the lock.
- `platform` shrinks and gets closer to what rule 8 says it is.

## Status

draft. Awaiting the owner. Item 1 is applied on branch `m-5-1` because it changes no behaviour; revert it if this ADR is rejected.

## Related

[Architecture assessment](../architecture/architecture-assessment.md) (C-1, C-2, C-3, E-1), [module catalogue](../architecture/module-catalogue.md), [ADR 0002](0002-modular-monolith.md), [ADR 0039](0039-spring-modulith-module-boundaries-and-outbox.md).
