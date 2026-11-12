# Authorization Requirements: Summary

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (summary). Per-requirement implementation status: [traceability](traceability.md).

A one-page index into the [authorization requirements](README.md). The requirement documents in this folder are the authoritative specification; this page states nothing they do not, and where the two differ the requirement document wins. Each ID links to its full text.

## In one paragraph

Kibootia gives applications one authorization foundation: applications declare their own resources, actions, scopes, relationships and conditions; administrators manage roles and assignments at runtime within the limits developers set; every protected backend operation is checked through one decision point; access is denied unless a rule allows it; and decisions and changes are recorded. Business workflows, validation and deadlines stay in the application ([scope and principles](scope-and-principles.md)).

## Status at a glance

40 Approved, 9 Proposed, 1 Open, 1 Deferred. The meaning of each status is in the [README](README.md#requirement-status). Of the 51, 4 are fully met by today's code, 21 partly and 25 not at all ([traceability](traceability.md)).

## Every requirement

"Phase" is the milestone of the [authorization roadmap](../../roadmap/authorization-roadmap.md) that delivers it.

| ID | Requirement | Status | Phase |
|---|---|---|---|
| [AUTH-001](model-and-principals.md#auth-001-extensible-authorization-model-definition) | Extensible authorization model definition | Approved | 8 to 11 |
| [AUTH-002](model-and-principals.md#auth-002-controlled-runtime-extensibility) | Controlled runtime extensibility | Approved | 15 |
| [AUTH-003](model-and-principals.md#auth-003-multiple-authorization-models) | Multiple authorization models | Approved | 8 to 11 |
| [AUTH-004](assignments-relationships-and-scopes.md#auth-004-contextual-and-scoped-assignments) | Contextual and scoped assignments | Approved | 9 |
| [AUTH-005](assignments-relationships-and-scopes.md#auth-005-relationship-based-authorization) | Relationship-based authorization | Approved | 10 |
| [AUTH-006](conditions-and-resource-access.md#auth-006-attribute-and-condition-based-authorization) | Attribute and condition-based authorization | Approved | 11 |
| [AUTH-007](assignments-relationships-and-scopes.md#auth-007-hierarchical-and-inherited-authorization-scopes) | Hierarchical and inherited authorization scopes | Approved | 9 |
| [AUTH-008](assignments-relationships-and-scopes.md#auth-008-multiple-simultaneous-and-contextual-roles) | Multiple simultaneous and contextual roles | Approved | 9 |
| [AUTH-009](assignments-relationships-and-scopes.md#auth-009-resource-specific-relationships-and-temporary-responsibilities) | Resource-specific relationships and temporary responsibilities | Approved | 10 |
| [AUTH-010](conditions-and-resource-access.md#auth-010-action-specific-authorization) | Action-specific authorization | Approved | 8 |
| [AUTH-011](conditions-and-resource-access.md#auth-011-workflow-aware-authorization) | Workflow-aware authorization | Approved | 11 |
| [AUTH-012](delegation-duties-and-approval.md#auth-012-delegation-and-acting-authority) | Delegation and acting authority | Approved | 14 |
| [AUTH-013](delegation-duties-and-approval.md#auth-013-multi-reviewer-and-approval-authorization) | Multi-reviewer and approval authorization | Approved | 14 |
| [AUTH-014](conditions-and-resource-access.md#auth-014-independent-resource-and-field-level-access) | Independent resource and field-level access | Approved | 11 |
| [AUTH-015](conditions-and-resource-access.md#auth-015-state-dependent-authorization) | State-dependent authorization | Approved | 11 |
| [AUTH-016](assignments-relationships-and-scopes.md#auth-016-flexible-organizational-and-resource-relationships) | Flexible organizational and resource relationships | Open | 9, 10 |
| [AUTH-017](assignments-relationships-and-scopes.md#auth-017-optional-multi-tenancy-and-organizational-isolation) | Optional multi-tenancy and organizational isolation | Approved | 16 |
| [AUTH-018](model-and-principals.md#auth-018-multiple-principal-types) | Multiple principal types | Approved | 12, 13 |
| [AUTH-019](model-and-principals.md#auth-019-flexible-group-membership-management) | Flexible group membership management | Approved | 13 |
| [AUTH-020](model-and-principals.md#auth-020-authorization-conflict-resolution) | Authorization conflict resolution | Approved | 7 |
| [AUTH-021](delegation-duties-and-approval.md#auth-021-time-bound-authorization) | Time-bound authorization | Approved | 9 |
| [AUTH-022](administration-audit-and-governance.md#auth-022-authorization-decision-auditing) | Authorization decision auditing | Approved | 7, 15 |
| [AUTH-023](administration-audit-and-governance.md#auth-023-authorization-simulation-and-policy-impact-analysis) | Authorization simulation and policy impact analysis | Deferred | Later |
| [AUTH-024](assignments-relationships-and-scopes.md#auth-024-configurable-permission-inheritance) | Configurable permission inheritance | Approved | 9 |
| [AUTH-025](assignments-relationships-and-scopes.md#auth-025-reusable-permission-bundles) | Reusable permission bundles | Approved | 15 |
| [AUTH-026](delegation-duties-and-approval.md#auth-026-separation-of-duties) | Separation of duties | Approved | 14 |
| [AUTH-027](administration-audit-and-governance.md#auth-027-authorization-administration-interface) | Authorization administration interface | Approved | 15 |
| [AUTH-028](administration-audit-and-governance.md#auth-028-administrator-hierarchy) | Administrator hierarchy | Approved | 7, 15, 16 |
| [AUTH-029](administration-audit-and-governance.md#auth-029-approval-of-sensitive-changes) | Approval of sensitive changes | Approved | 15 |
| [AUTH-030](administration-audit-and-governance.md#auth-030-configuration-lifecycle-management) | Configuration lifecycle management | Approved | 15 |
| [AUTH-031](integration-and-enforcement.md#auth-031-backend-enforcement) | Backend enforcement | Approved | 7 |
| [AUTH-032](integration-and-enforcement.md#auth-032-decision-caching) | Decision caching | Approved | 17 |
| [AUTH-033](integration-and-enforcement.md#auth-033-immediate-revocation) | Immediate revocation | Approved | Every phase |
| [AUTH-034](integration-and-enforcement.md#auth-034-consistent-authorization-interface) | Consistent authorization interface | Approved | 7 |
| [AUTH-035](integration-and-enforcement.md#auth-035-service-to-service-authorization) | Service-to-service authorization | Approved | 12 |
| [AUTH-036](integration-and-enforcement.md#auth-036-layer-level-enforcement) | Layer-level enforcement | Approved | 7, 9 |
| [AUTH-037](integration-and-enforcement.md#auth-037-developer-apis-and-sdks) | Developer APIs and SDKs | Approved | Every phase |
| [AUTH-038](integration-and-enforcement.md#auth-038-custom-authorization-definitions) | Custom authorization definitions | Approved | 8, 11 |
| [AUTH-039](integration-and-enforcement.md#auth-039-identity-provider-independence) | Identity-provider independence | Approved | 12 |
| [AUTH-040](integration-and-enforcement.md#auth-040-modular-authorization-capabilities) | Modular authorization capabilities | Approved | Every phase |
| [AUTH-041](delegation-duties-and-approval.md#auth-041-application-defined-exceptions-to-separation-of-duties) | Application-defined exceptions to separation of duties | Proposed | 14 |
| [AUTH-042](delegation-duties-and-approval.md#auth-042-reviewer-eligibility-and-optional-or-required-status) | Reviewer eligibility and optional or required status | Proposed | 14 |
| [AUTH-043](conditions-and-resource-access.md#auth-043-application-provided-workflow-revision-context) | Application-provided workflow revision context | Proposed | 11 |
| [AUTH-044](administration-audit-and-governance.md#auth-044-independent-authorization-of-audit-history-visibility) | Independent authorization of audit-history visibility | Proposed | 10 |
| [AUTH-045](delegation-duties-and-approval.md#auth-045-authorization-validity-versus-business-deadlines) | Authorization validity versus business deadlines | Proposed | 9 |
| [AUTH-046](assignments-relationships-and-scopes.md#auth-046-resource-ownership-based-authorization) | Resource ownership-based authorization | Proposed | 10 |
| [AUTH-047](assignments-relationships-and-scopes.md#auth-047-resource-ownership-and-scope-transfer) | Resource ownership and scope transfer | Proposed | 10 |
| [AUTH-048](conditions-and-resource-access.md#auth-048-bulk-operation-authorization) | Bulk operation authorization | Proposed | 11 |
| [AUTH-049](conditions-and-resource-access.md#auth-049-export-and-print-authorization) | Export and print authorization | Proposed | 11 |
| [AUTH-050](administration-audit-and-governance.md#auth-050-administrative-configuration-auditing) | Administrative configuration auditing | Approved | Every phase |
| [AUTH-051](conditions-and-resource-access.md#auth-051-collection-level-authorization) | Collection-level authorization | Approved | 9 |

## Owner decisions that shape the requirements

Taken on 2026-11-11; each is recorded where it applies.

| Decision | Recorded in |
|---|---|
| Every Approved requirement is committed scope, reconfirmed after the reference applications were read | Draft [ADR 0053](../../../decisions/0053-adopt-authorization-requirements.md) |
| Timetia and indent-edi validate the design and do not limit it; no integration with either is planned | [Reference scenarios](reference-scenarios.md) |
| Authorization is an in-process module, not a network service; the contract is kept as plain data | Draft ADR 0053 |
| Conditions are named conditions written by developers; administrators attach them | [AUTH-002](model-and-principals.md#auth-002-controlled-runtime-extensibility), draft [ADR 0054](../../../decisions/0054-authorization-domain-model.md) |
| Storage and evaluation stay in Kibootia on MySQL, confirmed by measurement | Draft [ADR 0056](../../../decisions/0056-authorization-evaluation-engine.md) |
| A user may assign only roles whose permissions they hold | [AUTH-028](administration-audit-and-governance.md#auth-028-administrator-hierarchy) |
| Breaking changes are acceptable: no forks, no users | Draft ADR 0053 |

## Rejected proposals

Considered and refused. Kept here so they are not proposed again without new evidence.

| Proposal | Rejected by | Reason |
|---|---|---|
| An expression language in which administrators type conditions, shipped now | Owner, 2026-11-11 | A new dependency and attack surface with no application that needs it; named conditions cover both reference applications. May return with a concrete need |
| An external authorization engine (OpenFGA, SpiceDB, Cerbos, Open Policy Agent) running by default | Owner, 2026-11-11, subject to the measurement in draft ADR 0056 | A second copy of the data or an extra service for every application; list queries that cannot be filtered in the database |
| Authorization as a standalone network service now | Owner, 2026-11-11 | No consumer needs it; indent-edi is being rebuilt. The contract is kept as data so it stays possible |
| Keycloak Authorization Services as the authorization store | Review; draft ADR 0056 | Ties authorization to the identity provider (AUTH-039) |
| A home-made policy or expression language | Review; draft ADR 0056 | Reinvention of what mature libraries provide |

## Recommendations awaiting the owner

Not decisions. Each is a **Review note** under the requirement it concerns.

| Requirement | Recommendation |
|---|---|
| AUTH-013 | Kibootia decides who may review; counting approvals and quorums stays in the application |
| AUTH-017 | A tenant isolates data; a department is a scope inside a tenant. Keep them separate mechanisms |
| AUTH-020 | Deny rules are an optional capability; when present they always win |
| AUTH-022 | A separate decision log, by default recording denials and sensitive allows only |
| AUTH-025 | Bundles only after scoped assignments and groups, and switchable |
| AUTH-029 | One fixed approval pattern, not a workflow engine |
| AUTH-030 | Draft, publish and rollback apply to definitions; assignments stay immediate |
| AUTH-032, 033 | Revocation wins over caching |
| AUTH-037 | "SDK" means the in-repository Java contract and the OpenAPI-described administration API |
| AUTH-040 | The baseline is today's behavior; everything beyond it has a switch |
