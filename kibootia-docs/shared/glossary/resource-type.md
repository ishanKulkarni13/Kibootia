# Resource type

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** not applicable (definition).

A kind of record a module protects, declared in code with a name (`notes.note`) and the relations a record can carry (`owner`). An action on a record names its resource type, and the type's relations are the [reaches](reach.md) a [grant](grant.md) of that action can have. The module builds a resource reference (type, ID, tenant, relations) from its record for every check, so the [policy decision point](policy-decision-point.md) never reads another module's tables. Decisions: [ADR 0054](../../decisions/0054-authorization-domain-model.md), [ADR 0055](../../decisions/0055-authorization-decision-contract.md) (drafts).
