# Authorization: Traceability

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** this document is the reference for how far each authorization requirement is implemented. Snapshot of commit `4cea34f` on 2026-11-11, from reading the code and tests; no build was run for this snapshot. "Verified" below relies on the CI runs recorded in [implementation status](../../roadmap/implementation-status.md).

Part of the [authorization requirements](README.md). Update a row in the same change that alters the code it describes.

## How to read it

| Support | Meaning |
|---|---|
| Full | The requirement is met by code that is covered by passing tests |
| Partial | Some of it exists; the Gap column says what is missing |
| None | Nothing in the repository implements it |
| Future | Deferred by the requirement itself |

"Phase" is the milestone of the [authorization roadmap](../../roadmap/authorization-roadmap.md) that delivers it. Evidence names classes and tests under `backend/src`; frontend paths are under `frontend/src`. What each gap would take, with its dependencies and its security and compatibility implications, is in the [gap analysis](gap-analysis.md).

Summary: 4 Full, 21 Partial, 25 None, 1 Future, out of 51.

## Model, principals and decision rules

| ID | Requirement | Support | Evidence today | Gap | Phase |
|---|---|---|---|---|---|
| AUTH-001 | Applications define their authorization model | Partial | Each module declares permissions in code through `PermissionProvider` (`NotesPermissions`, `AccessPermissions`); `PermissionRegistry` validates names at startup | No declared resource types, attributes, relationship types, scopes or inheritance rules; no versioning of the model | 8 to 11 |
| AUTH-002 | Administrators configure exposed capabilities at runtime | Partial | Roles are created, edited and assigned at runtime (`RoleService`, `RoleAssignmentService`, `AccessApiIT`); an editor can only grant what they hold | Only roles are configurable. No bundles, policies, relationships, scopes or attributes | 15 |
| AUTH-003 | RBAC, ABAC, ReBAC and scope-based models, combinable | Partial | RBAC is complete (`DefaultDecisionPoint`, `StoredPermissionSource`). Ownership and tenant are the only contextual rules. `ResourcePolicy` is an extension point with no implementation in main code | No attribute rules, no relationships, no scopes beyond all or own | 8 to 11 |
| AUTH-018 | Users, groups, nested groups, service accounts | Partial | Users only (`Caller`). Service-account tokens are rejected (`UserTokenValidator`, `ResourceServerIT`) | No groups; no service principals | 12 (service), 13 (groups) |
| AUTH-019 | Manual, rule-based and synchronized group membership | None | | Everything | 13 |
| AUTH-020 | Default deny, deny over allow, no inference, fail securely | Partial | Deny by default at the HTTP layer (`SecurityConfiguration`) and in the decision point; no decision point bean means deny all (`Authorization`); an undeclared action is an error; tenancy fails closed | No explicit deny rule exists, so "deny over allow" has nothing to act on; `ResourcePolicy` is first-match, not a combination | 7 |

## Assignments, relationships and scopes

| ID | Requirement | Support | Evidence today | Gap | Phase |
|---|---|---|---|---|---|
| AUTH-004 | Scoped assignments with validity, suspension, provenance | None | `role_assignment` holds tenant, user, role, `assigned_at`, `assigned_by` only | No scope, start, expiry or suspension. Provenance is who and when only | 9 |
| AUTH-005 | Relationship-based authorization | None | | Everything | 10 |
| AUTH-007 | Hierarchical scopes | None | | Everything | 9 |
| AUTH-008 | Several simultaneous, contextual roles | Partial | A user holds several roles and their permissions are combined (`StoredPermissionSource`) | Every role applies tenant-wide; none is contextual | 9 |
| AUTH-009 | Resource-specific and temporary relationships | None | | Everything | 10 |
| AUTH-016 | Hierarchies and overlapping relationships together | None | | Everything; also awaits confirmation (OQ-50) | 9, 10 |
| AUTH-017 | Optional multi-tenancy | Partial | Every table carries `tenant_id` with Hibernate `@TenantId`; no tenant bound means no rows; cross-tenant denial in `DefaultDecisionPoint`; isolation tests in `AuthorizationIT`, `NotesApiIT` | One default tenant. `TenantResolver` has no implementation; jobs hardcode the default tenant; no tenant administration | 16 |
| AUTH-024 | Configurable permission inheritance | None | | Everything | 9 |
| AUTH-025 | Reusable permission bundles | Partial | A role is a bundle of permissions and is audited | No bundle shared between roles; no "who is affected" preview; no developer-protected bundle other than the built-in roles | 15 |
| AUTH-046 | Ownership-based authorization | Partial | `Owned` and the own/any convention (`DefaultDecisionPoint`, `NotesApiIT`) | One fixed meaning of ownership; no creator, assignee or other distinction | 10 |
| AUTH-047 | Ownership and scope transfer | None | | Everything | 10 |

## Conditions and resource access

| ID | Requirement | Support | Evidence today | Gap | Phase |
|---|---|---|---|---|---|
| AUTH-006 | Attribute and condition-based rules | Partial | Three built-in facts: email verified, tenant, owner (`DefaultDecisionPoint`) | No application-defined attributes; `Caller` carries none | 11 |
| AUTH-010 | Action-specific authorization | Full | One permission per action, named `<module>.<resource>.<action>`; allowed and denied tests per operation (`NotesApiIT`, `AccessApiIT`, `UserAdministrationIT`) | Actions and permissions are the same string; separating them is design work, not a gap in behavior | 8 |
| AUTH-011 | Workflow-aware authorization | None | | Everything. `ResourcePolicy` could carry it but nothing uses it | 11 |
| AUTH-014 | Independent resource and field-level access | Partial | View, change, delete, restore and audit viewing are separate permissions; an invisible record answers 404 | No field-level rule (`UserResponse` always includes the email address); no separate export or history permissions | 11 |
| AUTH-015 | State-dependent authorization | None | | Everything | 11 |
| AUTH-043 | Workflow revision context | None | | Everything | 11 |
| AUTH-048 | Bulk operations | None | No bulk operation exists | Everything | 11 |
| AUTH-049 | Export and print | None | No export feature exists | Everything | 11 |
| AUTH-051 | Collection-level filtering | Partial | `Authorization.requireScope` returns all, own or none and `NoteService.list` turns it into a query condition (`NotesApiIT`) | Three fixed values; used by one module; the user list is all-or-403 | 9 |

## Delegation, time limits, duties and approval

| ID | Requirement | Support | Evidence today | Gap | Phase |
|---|---|---|---|---|---|
| AUTH-012 | Delegation | None | `AuditLog` records the acting user only | Everything | 14 |
| AUTH-013 | Multi-reviewer and approval primitives | None | | Everything | 14 |
| AUTH-021 | Time-bound assignments | None | | Everything | 9 |
| AUTH-026 | Separation of duties | None | Only fixed safeguards: nobody disables or deletes their own account (`UserAdministrationIT`) | No configurable static or dynamic rule | 14 |
| AUTH-041 | Exceptions to separation of duties | None | | Everything | 14 |
| AUTH-042 | Reviewer eligibility | None | | Everything | 14 |
| AUTH-045 | Validity versus business deadlines | None | | Follows from AUTH-021 | 9 |

## Administration, audit and governance

| ID | Requirement | Support | Evidence today | Gap | Phase |
|---|---|---|---|---|---|
| AUTH-022 | Decision auditing | None | A denial produces a 403 and nothing else (`GlobalExceptionHandler`); `runAsSystem` logs its reason at DEBUG | No decision record, no reason: the decision point returns a boolean | 7 (denials), 15 |
| AUTH-023 | Simulation and impact analysis | Future | | | Later |
| AUTH-027 | Administration interface | Partial | Screens for users, roles, role assignment and the audit log (`features/access`, `features/identity`, `features/audit`); they follow the caller's permissions | No screens for groups, scopes, relationships, delegations, policies or bundles | 15, with each phase |
| AUTH-028 | Administrator hierarchy | Partial | Three separate administrative permissions; an editor cannot grant what they do not hold; only an administrator grants the administrator role; last-administrator lock (`AccessApiIT`) | Role assignment does not check that the assigner holds the permissions of the roles they assign (`RoleAssignmentService.replaceRolesOfUser`; [security review](../../../temp/authorization-security-review.md), S1). One all-powerful built-in administrator role; no scope-limited or tenant-level administrators | 7 (assignment rule), 15, 16 |
| AUTH-029 | Approval of sensitive changes | None | | Everything | 15 |
| AUTH-030 | Configuration lifecycle | None | Roles have an optimistic version and an audit trail | No draft, validation step, publication, history or rollback | 15 |
| AUTH-044 | Independent audit-history visibility | Partial | Viewing the audit log needs `audit.entries.view`, separate from any resource permission (`AuditViewerIT`) | One log-wide permission; no per-resource or per-scope history visibility; viewing is not itself audited | 10 |
| AUTH-050 | Administrative configuration auditing | Partial | Role, permission and assignment changes are written in the same transaction as the change (`AuditService`, `AccessApiIT`) | Covers what exists; groups, relationships, delegations and publication do not exist yet | With each phase |

## Integration and enforcement

| ID | Requirement | Support | Evidence today | Gap | Phase |
|---|---|---|---|---|---|
| AUTH-031 | Backend enforcement | Full | Checks on use-case methods; ArchUnit rule 7 fails the build when a public use case has none (`AuthorizationRules`, `ArchitectureRulesFireTest`); direct API calls are refused in `access-denied.spec.ts` | Rule 7 does not see a check inside a lambda or helper | 7 |
| AUTH-032 | Decision caching | None | None, by decision ([ADR 0044](../../../decisions/0044-authorization-enforcement-conventions.md)) | Only if measurement shows a need | 17 |
| AUTH-033 | Immediate revocation | Full | Every decision reads the database; `TwoInstancesIT` shows a role change applying on a second instance at the next request | Must be kept true as caching, groups and inheritance arrive | Every phase |
| AUTH-034 | One authorization interface | Full | `Authorization` facade and the `PolicyDecisionPoint` port in `platform.authorization`; modules never touch the implementation (`ModularityTest`) | The contract must grow: it returns a boolean and takes an untyped resource | 7 |
| AUTH-035 | Service-to-service authorization | None | Service-account tokens are rejected; jobs and event handlers run as an all-powerful system caller | No service principal with narrow permissions | 12 |
| AUTH-036 | Enforcement at several layers | Partial | API: authentication on every path. Service: use-case checks. Data: tenant filter on every query | No resource-access-level rule beyond tenant and owner | 7, 9 |
| AUTH-037 | Developer APIs and SDKs | Partial | The Java API in `platform.authorization`; the administration API in `openapi.json` with a generated typed client | No API for anything beyond roles; "SDK" needs a definition (OQ-88) | Every phase |
| AUTH-038 | Custom resources, actions, attributes | Partial | Custom permissions per module, validated at startup (`AccessConfigurationTest`) | No resource-type or attribute definitions | 8, 11 |
| AUTH-039 | Identity-provider independence | Partial | Identity-provider administration sits behind a port (`IdentityProviderAdmin`); tokens are validated as generic OpenID Connect; roles are not read from the token | The subject must be a UUID; the service-account check and claim names follow Keycloak; one adapter | 12 |
| AUTH-040 | Modular capabilities | Partial | A tested mechanism exists for modules (`FilesDisabledIT`, the `module-removal` CI job) | No authorization capability has a switch, because none of the optional ones exists | Every phase |
