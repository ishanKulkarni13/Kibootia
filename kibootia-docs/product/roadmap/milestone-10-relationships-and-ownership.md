# Milestone 10: Relationships and Ownership

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists.

A phase of the [authorization roadmap](authorization-roadmap.md). Decision: draft [ADR 0054](../../decisions/0054-authorization-domain-model.md); an ADR on how relationships are stored and queried is written when the phase starts.

## 1. Objective

Let access follow from how a person is connected to one record: "the faculty member who teaches this lecture", "a reviewer requested on this ticket". After this phase a grant can reach "by relation" for any relation an application declares, not only `owner`.

## 2. Requirements covered

AUTH-005, AUTH-009, AUTH-016, and the proposed AUTH-044, AUTH-046 and AUTH-047 (each needs the owner's approval before it is built, OQ-80). Reference scenarios T2, T4, T5, T7. Security finding S10 in part.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| Resource type declarations | A resource type declares its relations; `owner` becomes one declared relation among others |
| `access/domain` decision point | Evaluates "by relation" for any declared relation |
| The access filter | Gains the alternative "records to which the principal has this relation" |
| `audit/application/AuditViewer` | A record's history can be requested for one record and is decided through the decision point |
| `access` role editor and API | A grant's reach can name any declared relation of the action's resource type |

## 4. New functionality delivered

- Relation types declared per resource type in code.
- Two kinds of relation: **derived**, read from a column the module already has (the owner of a note), and **stored**, a relationship record (a requested reviewer).
- Relationship records with an optional start and expiry, created through the owning module's use cases or a generic administration API, each write authorized and audited.
- Grants by relation, in the role editor.
- Creator, owner and assignee as separate relations where a resource type declares them (AUTH-046).
- A transfer use case pattern for owner and scope, with its own action and audit entry (AUTH-047).
- Per-record audit history: an action a role can grant by scope or by relation, so "the author and the requested reviewers may read this ticket's log" is expressible (AUTH-044).
- A switch for stored relationships. Derived relations, which is what ownership is today, stay on.

## 5. Modules affected

`platform`, `access`, `audit`, the frontend `access` and `audit` features, and the test fixture module. `notes` declares `owner` as a derived relation and is otherwise unchanged.

## 6. Dependencies

Milestone 8. Independent of milestone 9, though scenario T4 uses both. Draft ADR 0056 accepted or replaced.

## 7. Database changes and migration strategy

One new table in the `access` migration folder for relationship records: tenant, subject type and ID, relation, object type and ID, validity, and who created it and when, with indexes for both directions. No existing table changes: derived relations keep using the columns modules already have, so there is nothing to migrate.

## 8. Security implications

- A relationship record is a grant under another name. Writing one is authorized by its own action and audited, and the "only what you hold" rule applies: you cannot make someone a reviewer of a record you cannot review.
- An expired relationship never authorizes; expiry is checked at decision time.
- A relation grants nothing by itself; only a role's grant by that relation does (AUTH-007).
- Reading a record's history is recorded.
- A transfer changes access for people on both sides; it is one transaction and needs the right at the source and at the target.

## 9. Automated tests required

- Scenarios T2, T4, T5 and T7 as tests against the fixture module.
- A derived and a stored relation on the same resource type; a principal with two relations to one record.
- Relationship validity: not yet valid, valid, expired.
- Writing a relationship: allowed, denied, and denied for a relation the writer does not hold.
- Lists filtered by relation, with correct paging; a list combining a scope alternative and a relation alternative returns each record once.
- History visibility by relation and by scope; a student-like principal is refused; the read is recorded.
- Transfer: access moves with the record and the previous owner loses it on the next request.
- `TwoInstancesIT` extended for a relationship and its removal.
- The switch off: the application starts; stored relationships are ignored and grants by a stored relation match nothing.
- Query counts against the baseline.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), the [audit log](../../architecture/audit-log.md), glossary entries (relationship, relation), [how to add a feature](../../engineering/guides/add-a-feature.md), a recipe for the switch, the [threat model](../../operations/security/threat-model.md), [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), the storage ADR and an implementation-decisions ADR.

## 11. Acceptance criteria

1. The named scenarios pass with no application term in foundation code.
2. Ownership works exactly as before for the example feature, through the declared `owner` relation.
3. The role editor offers the relations of the resource type an action belongs to, and no others.
4. With the switch off, the tests of the previous phase pass.
5. All of the above pass in CI.

## 12. Rollback and recovery

The change is one additive table. Reverting the code or turning the switch off leaves it unused; grants by a stored relation then match nothing, which narrows access and never widens it. No recovery step is needed.

## 13. Usable state at the end of the phase

An application can say who is connected to a record and let roles follow that connection, for single records and for lists. Together with milestone 9 this covers Timetia's roles as read from its documents, apart from the rule that distinguishes theory lectures from lab and tutorial lectures, which is milestone 11.
