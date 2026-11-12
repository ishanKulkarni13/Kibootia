# Milestone 15: Administration and Governance

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists, apart from user, role and assignment administration with an audit log of administrative changes, built in milestone 3.

A phase of the [authorization roadmap](authorization-roadmap.md). ADRs on the definition lifecycle, on approval of sensitive changes and on the decision log (extending [ADR 0046](../../decisions/0046-audit-log-design.md)) are written when the phase starts.

## 1. Objective

Make the system governable: administrators with limited authority, authorization definitions that are validated and published rather than edited live, a second pair of eyes on dangerous changes, and a record of decisions that can be read and kept. After this phase an administrator's power is a role like any other, and a mistake in a definition cannot reach users before it is checked.

## 2. Requirements covered

AUTH-002, AUTH-022 (the stored decision log), AUTH-025, AUTH-027, AUTH-028, AUTH-029, AUTH-030, AUTH-050. No reference scenario: neither application documents these, so the scenarios are written from the requirement text.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| `access` role editing | Roles become **definitions**: edited as drafts, validated, published, versioned |
| `access` assignment, membership, relationship and delegation writes | Stay immediate; optionally held for approval |
| Administration actions (roles, assignments, scopes, groups) | Granted at scope nodes, so a scoped administrator is possible |
| Decision records of milestone 7 | Stored in a decision log with retention, a viewer and metrics |
| `audit` viewer | Reading the log or the decision log is itself recorded |
| `frontend/src/features/access`, `features/audit` | Screens for every administrable thing built so far, unified; the draft, publish and rollback flow |

## 4. New functionality delivered

- **The definition lifecycle:** roles, bundles, duty rules and inheritance settings are edited as a draft, validated (every action, scope type, relation and condition must be declared; nothing may exceed the publisher's own authority), published, versioned and rolled back. Rollback restores definitions only and never an assignment.
- **Permission bundles:** a named set of grants shared by several roles, with a preview of the roles and principals a change affects, and developer-protected bundles that cannot be edited.
- **The administrator hierarchy:** administration actions granted at scope nodes; the built-in administrator is an ordinary role with explicit grants; a role-assign right at a node is limited to that node and below; the last-administrator safeguard works per scope.
- **Approval of sensitive changes:** one fixed pattern. An application lists which changes are sensitive; such a change waits as a pending change until a configured number of approvers other than the requester have approved, each needing the right to make the change.
- **The decision log:** stores principal, action, resource reference, outcome, reason code and trace identifier, with no attribute values; by default denials and sensitive allows; a switch for every allow; retention; a viewer; access to it recorded.
- Administrative screens completed and unified, each following the caller's own authority.
- Metrics: decisions by outcome and reason.
- Switches for bundles, the lifecycle, approvals and the log of allows.

## 5. Modules affected

`access` (most of the change), `audit`, `platform` (decision records), the frontend `access` and `audit` features. The `module-removal` matrix gains a row for the optional capabilities.

## 6. Dependencies

Milestones 8 to 11 for the definitions that exist (roles, grants, conditions); milestones 13 and 14 if present, since their definitions join the lifecycle.

## 7. Database changes and migration strategy

Additive, in `access` and `audit`: a definition-version table holding published and draft versions (existing roles become version 1 of themselves); bundle tables; a pending-change table; a decision-log table in the `audit` folder, indexed by time and principal, with the retention job extended. The live tables remain the single source for evaluation: publication writes them in one transaction, so a decision never reads a draft.

## 8. Security implications

- **Configuration is an escalation path.** The "only what you hold" rule applies to publishing a definition; validation refuses a definition that references something undeclared or exceeds the publisher.
- Rollback must never resurrect a revoked assignment. Definitions and assignments are separate stores, and a test publishes, assigns, revokes, rolls back and checks.
- The pending-change pattern must not be satisfiable by the requester or by one person using two accounts' roles; approvers are checked against the requester's identity and the right to make the change.
- The decision log can leak by what it records. It stores references and codes, never values, and reading it is recorded.
- Scoped administrators change the last-administrator rule, which exists to prevent lock-out; the tenant-root administrator safeguard stays.
- The log's volume is a denial-of-service surface: defaults record denials and sensitive allows only, and retention bounds the table.

## 9. Automated tests required

- Lifecycle: an invalid draft cannot be published; a valid one applies in one transaction; a decision during publication sees the old or the new version, never a mixture; rollback restores definitions and leaves assignments alone.
- Bundles: a change shows the affected roles first; a protected bundle cannot be edited; a bundle change goes through the lifecycle.
- Hierarchy: a node administrator assigns inside the node and is refused outside; the administrator role is ordinary; the last tenant-root administrator cannot be removed.
- Approval: the requester cannot approve; the configured number of approvers is required; an approver without the right is refused; an unapproved change has no effect.
- Decision log: a denial is stored; an allow of a sensitive action is stored; an ordinary allow is stored only with the switch on; no attribute value appears; reading is recorded; retention purges.
- `TwoInstancesIT` extended for publication and its rollback.
- Each switch off: the application starts and behaves as after the previous phase.
- Screens: Vitest and end-to-end coverage for each, with denied cases and an accessibility check, following the pattern of milestone 3.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), the [audit log](../../architecture/audit-log.md), the [observability baseline](../../operations/monitoring/observability-baseline.md), the [threat model](../../operations/security/threat-model.md), [application configuration](../../operations/configuration/application-configuration.md), glossary entries (definition, bundle, pending change, decision log), recipes for the switches, [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), the three ADRs and an implementation-decisions ADR.

## 11. Acceptance criteria

1. No sequence of publish, assign, revoke and rollback restores revoked access.
2. A scoped administrator cannot affect anything outside their node, by any endpoint (one test per administration endpoint).
3. A sensitive change has no effect until approved by someone other than its requester.
4. The decision log never contains an attribute value, and reading it is recorded.
5. With each switch off, the tests of the previous phases pass.
6. All of the above pass in CI.

## 12. Rollback and recovery

Definitions are versioned, so a bad publication is undone by publishing the previous version: that is the feature itself. The new tables are additive. If the lifecycle switch is turned off, the live tables, which always hold the last published version, keep serving decisions and edits become direct again, with each audited.

## 13. Usable state at the end of the phase

Kibootia can be run by several administrators of different reach, whose changes to roles are checked before they take effect and whose dangerous changes need a second person, with a readable record of what was refused. An application that turns every optional capability off keeps today's simple administration.
