# Authorization scope

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** not applicable (definition).

The answer to "which records may this caller see or change?". Since contract version 2 it is an **access filter**: nothing, everything in the tenant, or records to which the caller has a relation (today: the records they own). A module turns it into a query condition so that lists are filtered in the database and paging stays correct. Contract version 1 called it a scope with the values `ALL`, `OWN` and `NONE`. Decisions: [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md), [ADR 0055](../../decisions/0055-authorization-decision-contract.md) (draft); see also [policy decision point](policy-decision-point.md).
