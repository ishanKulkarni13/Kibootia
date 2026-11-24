# Policy decision point

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** not applicable (definition).

The single place in the backend that answers "may this caller perform this action on this resource?". Every protected operation asks it. It answers with a **decision** (allow or deny, a stable reason code, and what decided) and, for lists, with an [access filter](authorization-scope.md). Modules may add rules that only narrow what grants allow, and an external engine can replace the evaluation because it is a [port](port.md). Decisions: [ADR 0027](../../decisions/0027-central-authorization-decision-point.md), [ADR 0055](../../decisions/0055-authorization-decision-contract.md).
