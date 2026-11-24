# Reach

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** not applicable (definition).

How far a [grant](grant.md) goes: `all`, every record of the tenant, or `relation:<name>`, only the records to which the holder has that relation, such as `relation:owner` for their own. An action on the collection has only `all`; an action on a record can have the relations its [resource type](resource-type.md) declares. Replaced the `-any` suffix of earlier permission names. Decision: [ADR 0054](../../decisions/0054-authorization-domain-model.md).
