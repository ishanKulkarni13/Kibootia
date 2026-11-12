# Milestone 14: Delegation, Separation of Duties and Reviewer Rules

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists. The audit log records the acting user only, by design ([ADR 0046](../../decisions/0046-audit-log-design.md)).

A phase of the [authorization roadmap](authorization-roadmap.md). Decisions: draft [ADR 0054](../../decisions/0054-authorization-domain-model.md); ADRs on delegation, on separation of duties and on the audit identity (extending ADR 0046) are written when the phase starts.

## 1. Objective

Let one person act with another's authority for a limited time, and let an application forbid combinations of powers that should check each other. After this phase a delegation is a bounded, audited grant that can never exceed what its giver holds, and an application can declare that some roles, or some actions on one record, may not be combined.

## 2. Requirements covered

AUTH-012, AUTH-013 (eligibility and the optional self-approval restriction), AUTH-026, and the proposed AUTH-041 and AUTH-042 (owner approval needed, OQ-80). Reference scenarios: T4 in full (eligibility by relation). Timetia's "HOD acts for an absent faculty" is a role power there and does not need delegation. Neither application documents delegation, a duty rule or a quorum: Timetia states that self-approval is always allowed, so the restriction is off by default.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| `audit/AuditEntry`, `AuditLog`, the audit table | A second identity: who acted, and under whose authority |
| Decision records of milestone 7 | The same second identity |
| `access` assignment writes (user, group, delegation) | Run the static duty check |
| `access/domain` decision point | Evaluates delegations against the delegator's current authority; applies dynamic duty exclusions |
| `access` administration | Screens for delegations and duty rules |

## 4. New functionality delivered

- Delegations: delegator, delegate, scope node, permitted actions, validity, constraints, revocation. Evaluated on every decision against what the delegator holds **at that moment**.
- Limits: a delegation cannot exceed the delegator's authority, cannot include the power to assign roles or to delegate, and cannot be passed on unless an application enables that.
- Static duty rules: pairs of roles that one principal may not hold in the same scope, checked when an assignment, a membership or a delegation is written, including through groups.
- Dynamic duty rules: an action is refused when the principal has a stated relation to the record, such as "creator" (the "not the author" rule); an application turns each on.
- Scoped exceptions to a duty rule, as data, audited, never global (AUTH-041).
- Reviewer eligibility: what already exists as grants by relation, documented as the pattern; counting approvals stays in the application (AUTH-013, AUTH-042).
- Switches for delegation and for duty rules.

## 5. Modules affected

`audit` (the entry and the viewer), `access`, `platform` (contract), the frontend `access` and `audit` features, and the test fixture module.

## 6. Dependencies

Milestones 9 and 10 (scope and relations), milestone 13 if groups exist (the static check must see group assignments), and milestone 15 for the final form of the audit and decision records, though this phase adds the second identity itself.

## 7. Database changes and migration strategy

Additive: a delegation table in `access`; a duty-rule table and an exception table in `access`; in `audit`, a nullable column for the authority under which an entry was made, with existing rows left empty (meaning "acted on own authority"). The audit table is append-only, so this is the only change allowed to it, and it is a column added, not a row rewritten.

## 8. Security implications

- Delegation abuse is the central risk: a delegate who keeps power after the delegator loses it, who gets more than the delegator has, or who delegates onward. Evaluating against the delegator's current authority, and forbidding the assign and delegate powers, remove the three.
- A duty rule that sees only direct assignments is bypassed through a group or a delegation; the check runs on all three paths and a test tries each.
- Exceptions weaken rules by design; each is scoped, expires, and is audited, and cannot weaken an unrelated rule.
- The audit entry now names two people. Display follows the existing rule: people are referred to by identifier, and an erased user shows as erased.
- The dynamic "not the author" rule needs the creator as a fact about the record; the application supplies it as a relation. Kibootia keeps no history of who did what to a business record.

## 9. Automated tests required

- Delegation: valid; expired; revoked; delegator loses the authority and the delegation stops on the next request; an attempt to exceed the delegator; an attempt to delegate onward; an attempt to delegate the assign power.
- Static duties: refused directly, through a group and through a delegation; an exception allows it in one scope only.
- Dynamic duties: refused for the creator, allowed for another holder of the same role; off by default.
- Audit: both identities recorded; old entries still display; the viewer shows the authority.
- `TwoInstancesIT` extended for a delegation and its revocation.
- Switches off: the application starts; delegated grants match nothing and duty rules are not applied.
- The quorum case: a test fixture shows an application passing an approval count in as an attribute and a named condition using it, with no counting in Kibootia.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), the [audit log](../../architecture/audit-log.md), glossary entries (delegation, separation of duties), the [threat model](../../operations/security/threat-model.md), recipes for the two switches, [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), the three ADRs and an implementation-decisions ADR.

## 11. Acceptance criteria

1. No test finds a delegation exceeding, outliving or passing on its giver's authority.
2. The static duty check holds on all three paths.
3. Self-approval is allowed unless an application turns the restriction on.
4. Every existing audit test passes, and old entries display.
5. With the switches off, the tests of the previous phases pass.
6. All of the above pass in CI.

## 12. Rollback and recovery

The changes are additive. Turning a switch off ignores delegations and duty rules; ignoring a duty rule widens what a principal may do, so the recipe says to review assignments made under it first. The new audit column is nullable and unused by older code.

## 13. Usable state at the end of the phase

An application can let a department head cover for a colleague for a week, and forbid a requester from approving their own request, with every use attributed to both people. Applications that use neither see no change.
