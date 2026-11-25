# 0068 Apache License 2.0

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** observed (documentation and metadata): `LICENSE`, `NOTICE`, the `licenses` of `backend/pom.xml` and the `license` of both `package.json` files.

## Context

Kibootia is a starter that an application copies and then owns ([ADR 0023](0023-template-fork-adoption-model.md)). Until now the repository had no licence, so copying it granted no right to use it, which defeats the model. OQ-48 asked for the choice and blocked the first release. The owner delegated the choice on 2026-11-25 and named Apache-2.0.

## Decision

Kibootia is licensed under the Apache License, Version 2.0, in the repository's `LICENSE`, with a `NOTICE` and the licence declared in `pom.xml` and both `package.json` files.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| MIT | Shorter, but without the express patent grant that an enterprise starter that others build products on should give |
| A copyleft licence (AGPL) | A fork is meant to be closed and private; a copyleft condition would defeat the adoption model |
| No licence | Grants no right to copy, which the adoption model needs |

## Consequences

- A fork may use, change and distribute Kibootia, including in closed products, if it keeps the licence and the notice.
- The dependency review job can fail on a licence that is not allowed once a policy of allowed licences is written; none is written yet.
- The copyright line in `NOTICE` says "the Kibootia authors". The owner may put a legal name there.

## Status

accepted on the owner's delegation of 2026-11-25. Resolves OQ-48.

## Related

- [ADR 0023](0023-template-fork-adoption-model.md), [milestone 6](../product/roadmap/milestone-6-production-readiness.md), [ADR 0061](0061-ci-quality-and-supply-chain-gates.md).
