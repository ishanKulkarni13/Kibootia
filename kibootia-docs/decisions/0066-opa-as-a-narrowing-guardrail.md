# 0066 Open Policy Agent as a narrowing guardrail

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** observed on branch `m-policy` (local run, 2026-11-24; CI run pending). Recorded in [implementation status](../product/roadmap/implementation-status.md).

## Context

[ADR 0027](0027-central-authorization-decision-point.md) put every check behind one decision point and made OPA a recipe; draft [ADR 0056](0056-authorization-evaluation-engine.md) keeps the evaluation of grants in Kibootia. Their reasons still hold for OPA as **the** decision point: a second copy of the role model, list queries that cannot be filtered in the database, and an outage that denies everything.

The owner asked on 2026-11-24 for OPA to be used "if it fits". It fits in a different role, which the combination rule of [ADR 0055](0055-authorization-decision-contract.md) makes safe: a module rule can only narrow what grants allow. Enterprises commonly want policies that are owned outside an application and apply above its roles: a change freeze, a blocklist of compromised accounts, a rule that some actions happen only from a managed network. Those are refusals, never grants.

## Decision

1. **OPA is an optional guardrail, not the decision point.** A new removable module, `policy`, registers an OPA adapter (`OpaGuardrail`) as a `ResourceRule`. It is consulted only after Kibootia's grants have allowed, only for the actions listed in `kibootia.policy.opa.actions` (names or `*` patterns), and never for the system. Grants, reach, deny grants, list filters and the role editor are unchanged.
2. **Rules may now speak about an action as a whole.** A `ResourceRule` is also consulted for an action on the collection (no record), and a list is refused entirely when a rule objects to its action. Without this a guardrail could not refuse creating, administering or purging. It is still narrowing only.
3. **The policy answers with reasons.** Kibootia sends `POST /v1/data/<path>` with an input of identifiers only (action, principal kind, ID and tenant, and the resource's type, ID, tenant and relations) and expects `{"result": [reason, ...]}`; an empty set is no objection. The default path is `kibootia/guardrails/deny`. Decision records name the rule (`opa:kibootia/guardrails/deny`) and the reason code `authorization.refused_by_rule`.
4. **It fails closed, for its actions only.** An unreachable or slow OPA (connect 0.5 s, read 1 s by default), an error answer, a malformed answer, or a path with no policy (OPA answers `{}`) refuses with `authorization.evaluation_error`, logged as an error. Every action not listed is decided without OPA, so an outage stops only what the policy guards.
5. **Off by default, a switch and a profile.** `kibootia.policy.opa.enabled=false`; Compose profile `policy` runs `openpolicyagent/opa:1.22.2` with the shipped policy in `infra/opa`, which objects to nothing until its data lists something. Startup fails when the guardrail is on without an address or without any action. The module is removable like `files` and `notes` (architecture rule 5).
6. **The shipped policy is tested.** `infra/opa/guardrails_test.rego` runs with `opa test` in its own CI job, on the same OPA version.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| OPA as the decision point behind the port | The reasons of ADR 0027 and ADR 0056: a second model, no database filter for lists (partial evaluation would have to be translated into SQL per store), and an outage that stops every request |
| Ask OPA on every decision | Every request would wait on it and fail with it; the guardrail is for chosen actions |
| Fail open when OPA cannot answer | A guardrail that disappears during an outage, or under an attack on OPA, is no guardrail |
| Let OPA allow as well as refuse | It would bypass roles, the role editor and the audit trail as the description of who may do what |
| Cerbos or OpenFGA instead | Both are candidates for the engine question of ADR 0056, which is about evaluating grants and relationships, not about guardrails |

## Consequences

- Kibootia gains a place for policy owned outside it, at the cost of one HTTP call per guarded decision and one more service to run where it is used.
- A refusal by OPA is a 403 like any other, and the decision record says which rule and that it was a rule.
- A policy cannot hide some records of a list; it can only refuse the list.
- ADR 0027 stays accepted: OPA as the decision point remains a recipe for a fork that wants it.

## Status

draft. Awaiting the owner.

## Related

[ADR 0027](0027-central-authorization-decision-point.md), [ADR 0055](0055-authorization-decision-contract.md), [ADR 0056](0056-authorization-evaluation-engine.md), [recipe](../recipes/enable-opa-guardrails.md), [authorization](../architecture/identity/authorization.md).
