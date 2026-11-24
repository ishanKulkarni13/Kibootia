# Grant

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** not applicable (definition).

One entry of a role: an action, how far it [reaches](reach.md), and whether it allows or denies. A grant is the only thing that gives access; a deny grant takes an action away whatever other roles give, and decides over every allow. Written `notes.notes.update/relation:owner` in audit entries and decision records, with `!` before a deny. Decision: [ADR 0054](../../decisions/0054-authorization-domain-model.md) (draft); built in [milestone 8](../../product/roadmap/milestone-8-grant-model.md). See also [resource type](resource-type.md).
