# Milestone 11: Named Conditions and Data Operations

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists.

A phase of the [authorization roadmap](authorization-roadmap.md). Decisions: draft [ADR 0054](../../decisions/0054-authorization-domain-model.md) (named conditions) and the owner's decision of 2026-11-11 that no expression language ships now.

## 1. Objective

Let a grant depend on a fact about the record or the request: "only theory lectures", "only while it is a draft". After this phase an application declares the facts it supplies, developers write conditions over them in Java, and administrators attach those conditions to grants. The same phase adds the three ways data leaves a normal view: single fields, many records at once, and export or print.

## 2. Requirements covered

AUTH-006, AUTH-011, AUTH-014, AUTH-015, and the proposed AUTH-043, AUTH-048 and AUTH-049 (owner approval needed, OQ-80). Reference scenarios T3 (lecture kind), T6, E2, E5, E6. Security finding S2.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| Resource type declarations | A resource type declares its attributes (name and type) and its sensitive field groups |
| The request context of milestone 7 | Use cases pass workflow facts (a state, a revision) in it |
| `access/domain` decision point | Evaluates a grant's named conditions after reach |
| The access filter | Gains the alternative "records matching this named condition", in the condition's database form |
| `access` role editor and API | A grant can carry named conditions chosen from a list |
| Response mappers and update handlers, starting with `identity/web/UserResponse` | Ask for a decision per sensitive field group |

## 4. New functionality delivered

- Declared attributes per resource type, supplied from the server's own record.
- Named conditions: a Java class with a name, a description, the resource type it applies to, a test for one record and, optionally, a database form for lists.
- Attaching conditions to grants in the role editor; a condition with no database form can be attached to instance actions only, checked when it is attached.
- State-dependent and workflow-aware rules as named conditions over attributes the application passes in. Kibootia stores no workflow state.
- Sensitive field groups: each group has its own action; a helper lets a mapper include or omit the group, and an update handler refuse a change to it.
- Bulk decisions: one call decides for many records and returns a decision each; the use case chooses all-or-nothing or skip-unauthorized. Decisions are made at execution.
- Export and print as ordinary actions with their own grants and their own list filter.
- A switch for named conditions. With it off, a grant that carries a condition matches nothing.

## 5. Modules affected

`platform`, `access`, `identity` (the first sensitive field group: a profile's email address), the frontend `access` feature, and the test fixture module, which carries the state, export and bulk scenarios because the foundation has no export feature of its own.

## 6. Dependencies

Milestones 9 and 10, because conditions combine with scope and relation reach and the scenarios use all three.

## 7. Database changes and migration strategy

One additive change in the `access` migration folder: a link between a grant and the names of its conditions. Names are stored, not code, so a condition that no module declares any more makes the grant match nothing, the same rule that applies to undeclared permissions today. No existing row changes.

## 8. Security implications

- Attributes come from the record the server loaded, never from the request body, or a caller could assert their way past a condition.
- A condition can only narrow a grant. It cannot allow what the grant's reach does not.
- A field group that is forgotten on one endpoint leaks; a test per resource type enumerates every response type that carries a protected field.
- An export must use the export action's filter, not the view filter.
- Bulk operations run inside one transaction and see permissions as of its start; the phase states the limit on how long such an operation may run and tests a revocation committed during it.
- The "only what you hold" rule covers conditions: an editor cannot remove a condition from a grant to widen it beyond what they hold.

## 9. Automated tests required

- Scenarios T3, T6, E2, E5 and E6 as tests against the fixture module.
- A condition with and without a database form: attachable to a list action or refused.
- An undeclared condition name makes the grant match nothing; startup does not fail, as for undeclared permissions.
- Field groups: included, omitted and refused on update; the enumeration test for response types.
- Bulk: all authorized, some unauthorized under each of the two modes, and a revocation during the operation.
- Export: a caller with view but not export is refused; the exported set equals the export filter, not the view filter.
- Lists combining scope, relation and condition alternatives page correctly.
- The switch off: the application starts and conditional grants match nothing.
- Query counts against the baseline.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), [API conventions](../../architecture/api/conventions.md) (omitted fields), glossary entries (attribute, named condition, field group), [how to add a feature](../../engineering/guides/add-a-feature.md) (writing a condition; protecting a field; a bulk action), a recipe for the switch, the [threat model](../../operations/security/threat-model.md), [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), and an implementation-decisions ADR.

## 11. Acceptance criteria

1. The named scenarios pass with no application term in foundation code and no expression language in `pom.xml`.
2. A profile's email address is returned only to a caller granted its field group, and the existing profile tests pass with the built-in roles granted that group.
3. No condition reads a value from the request body (a test or an architecture rule shows it).
4. With the switch off, the tests of the previous phases pass.
5. All of the above pass in CI.

## 12. Rollback and recovery

One additive link table. With the switch off or the code reverted, conditional grants match nothing, which narrows access. The email field group changes a response: reverting the code restores the previous response shape; the OpenAPI file and the generated client are regenerated in the same change either way.

## 13. Usable state at the end of the phase

Everything read in Timetia's documents and everything in indent-edi's matrix can be expressed: roles at scopes, relations to records, a condition on the kind or state of a record, a separate export permission. Applications that declare no attribute or condition are unaffected.
