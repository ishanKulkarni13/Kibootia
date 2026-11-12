# Kibootia — Authorization System Requirements

**Document type:** Functional and non-functional requirements  
**Status:** Requirements agreed during discovery; ready for review  
**Scope:** Reusable, extensible authorization platform for Kibootia

## 1. Purpose

Kibootia must provide a reusable, enterprise-grade authorization system that can serve different types of applications without requiring each application to build its own authorization infrastructure.

The system must support flexible authorization models, contextual access, organizational structures, relationships, delegated authority, policy administration, auditing and developer integration.

It must remain domain-agnostic and configurable. Applications should be able to enable the capabilities relevant to their needs without being forced to adopt the entire authorization feature set.

## 2. Core design principles

1. **Domain independence:** No mandatory assumptions about colleges, hospitals, companies or other business domains.
2. **Controlled extensibility:** Developers define the supported authorization capabilities; administrators configure them within those boundaries.
3. **Configurable complexity:** Applications choose which capabilities to enable.
4. **Secure by default:** Unspecified access is denied.
5. **Separation of concerns:** Authentication, authorization and business workflows have distinct responsibilities.
6. **Backend enforcement:** Frontend permission checks are never the security boundary.
7. **Auditable administration:** Authorization changes and decisions must be traceable.
8. **Future compatibility:** Optional capabilities should not make future expansion unnecessarily difficult.

## 3. Functional requirements

### 3.1 Authorization model and extensibility

**AUTH-001 — Extensible authorization model definition**

Applications must be able to define their authorization models, including resource types, actions, attributes, relationships, default roles, permission definitions and policies.

Developers define which components administrators may customize. Administrators can configure supported extensions without changing application code or redeploying the application.

Model changes must be validated, versioned, auditable and recoverable.

**AUTH-002 — Controlled runtime extensibility**

Administrators can extend developer-provided authorization capabilities within application-defined boundaries.

They cannot introduce arbitrary executable business operations through authorization configuration.

Applications can decide which extension points are configurable.

**AUTH-003 — Multiple authorization models**

The platform must support:

- RBAC — Role-Based Access Control.
- ABAC — Attribute-Based Access Control.
- ReBAC — Relationship-Based Access Control.
- Scope-based authorization.

Applications can use these independently or combine them in a single authorization decision.

### 3.2 Scopes, relationships and contextual access

**AUTH-004 — Contextual and scoped assignments**

Authorization assignments must support global and resource-scoped access.

Possible scopes include institutions, departments, faculties, courses, classes, batches, projects and individual resources.

Assignments must support multiple simultaneous contexts, validity periods, revocation and assignment provenance.

**AUTH-005 — Relationship-based authorization**

Applications must be able to define relationships between users, groups and resources.

Examples include faculty assigned to a course, students enrolled in a course and reviewers assigned to a ticket.

Authorization decisions must be able to consider these relationships.

**AUTH-006 — Attribute and condition-based authorization**

Policies must be able to evaluate application-defined attributes and contextual conditions.

Examples include department, academic year, resource status, ownership, assignment validity and request context.

The platform must not hardcode domain-specific business attributes.

**AUTH-007 — Hierarchical and inherited authorization scopes**

The platform must support hierarchical scopes and configurable inheritance.

Applications determine which permissions propagate to child scopes and which do not.

**AUTH-008 — Multiple simultaneous and contextual roles**

A user can hold multiple roles and responsibilities simultaneously, with different scopes and contexts.

Authorization must consider the relevant assignment for the requested action rather than relying on one global role.

**AUTH-009 — Resource-specific relationships and temporary responsibilities**

Relationships and responsibilities must be assignable to individual resources, independently of organizational roles.

They may be temporary, time-bound or independently revoked.

**AUTH-010 — Action-specific authorization**

Authorization must evaluate individual actions rather than treating access to a resource as unrestricted permission.

Viewing, modifying, approving, rejecting, deleting and reverting must be independently controllable.

**AUTH-011 — Workflow-aware authorization**

Authorization policies may consider workflow state and requested transitions.

The consuming application owns its workflow engine, valid transitions and business lifecycle. Kibootia supplies authorization capabilities for evaluating access to those operations.

**AUTH-012 — Delegation and acting authority**

The platform must support controlled delegation of responsibilities.

Delegation must have explicit scope, permitted actions, validity periods and audit records.

Delegated authority cannot exceed the delegator's authority.

**AUTH-013 — Multi-reviewer and approval authorization**

The platform must support authorization primitives for multi-reviewer scenarios.

Applications can define eligible reviewers, required reviewers, all/any/quorum requirements, rejection authority, escalation authority and self-approval restrictions.

The consuming application owns the actual approval workflow.

### 3.3 Resource and data access

**AUTH-014 — Independent resource and field-level access**

The platform must support separate authorization for:

- Resource discovery and listing.
- Viewing individual resources.
- Accessing sensitive or restricted fields.
- Creating and modifying resources.
- Modifying specific fields.
- Performing actions and workflow transitions.
- Accessing history and audit records.
- Exporting and downloading information.

List and search APIs must support authorization-aware filtering so inaccessible resources are not exposed.

**AUTH-015 — State-dependent authorization**

Authorization decisions may depend on resource state, requested transition, ownership, relationships, workflow participants, time and application-defined attributes.

The consuming application defines its valid business states and transitions.

**AUTH-016 — Flexible organizational and resource relationships**

The platform must support application-defined organizational structures and overlapping relationships between users, groups and resources.

Inheritance and propagation must be configurable rather than automatically assumed.

*Review note: The combination of hierarchical structures and overlapping relationships is the proposed platform direction; the precise relationship model remains an architectural decision.*

### 3.4 Tenancy and organizational structures

**AUTH-017 — Optional multi-tenancy and organizational isolation**

Kibootia must support both single-tenant and multi-tenant applications.

- Single-tenant applications must not be forced to configure organizations or tenants.
- Multi-tenant applications must be able to isolate users, resources, assignments, relationships and policies.
- Tenant-scoped administration must be supported.
- Cross-tenant administration requires explicit authority.
- Applications define their own organizational structures.
- Tenant-aware authorization applies when multi-tenancy is enabled.

Organizational concepts such as departments, divisions and institutions are optional rather than mandatory platform concepts.

### 3.5 Principals and group management

**AUTH-018 — Multiple principal types**

Authorization assignments must support:

1. Individual users.
2. Groups of users.
3. Roles assigned to groups.
4. Nested groups.
5. Service accounts and automated system identities.

Nested membership must prevent cycles and ambiguous inheritance.

Service identities must have their own identity and lifecycle rather than impersonating ordinary users.

Assignments must support appropriate scope, validity, revocation and audit information.

**AUTH-019 — Flexible group membership management**

The platform must support three membership mechanisms:

- Manual membership managed by administrators.
- Rule-based membership using configurable attributes and conditions.
- External synchronization from identity providers and directories.

These mechanisms must be independently configurable or combinable.

The system must define how externally managed and manually managed memberships interact. Membership changes must be auditable and consistently reflected in authorization decisions.

### 3.6 Decision resolution and access lifecycle

**AUTH-020 — Authorization conflict resolution**

Default authorization behaviour must follow:

- Default deny.
- Explicit deny takes precedence over an applicable allow.
- No implicit permission inheritance between unrelated actions.
- Fail securely when a decision cannot be evaluated reliably.

**AUTH-021 — Time-bound authorization**

Assignments and policies must support:

- Start and expiry dates/times.
- Automatic expiration.
- Manual revocation.
- Authorized renewal or extension.
- Scope-specific validity.
- Audit records of grants, renewals, extensions and revocations.
- Correct time-zone handling.

Expired assignments must no longer grant access, even if their records remain stored.

**AUTH-022 — Authorization decision auditing**

The platform must maintain authorization decision logs containing:

- Actor identity.
- Requested action.
- Target resource.
- Allow or deny outcome.
- Decision reason.
- Relevant policy context.
- Timestamp.
- Correlation with application request identifiers where available.

The platform must also maintain separate administrative audit records for changes to authorization configuration.

Logs must be access-controlled, tamper-resistant and subject to configurable retention. Sensitive information must not be unnecessarily exposed.

**AUTH-023 — Authorization simulation and policy testing**

**Classification: Future Scope**

Future versions may support:

- Simulating authorization decisions without applying changes.
- Testing proposed policy changes.
- Previewing users and groups that gain or lose access.
- Comparing decisions before and after changes.
- Identifying conflicting or unreachable policies.

This is not mandatory for the initial release.

### 3.7 Permission inheritance and reusable permissions

**AUTH-024 — Configurable permission inheritance**

The platform must support configurable permission inheritance across hierarchical scopes and supported resource relationships.

Applications define which permissions propagate, where propagation stops and which restrictions apply.

Administrators must be able to understand the origin of inherited permissions.

**AUTH-025 — Reusable permission bundles**

The platform must support reusable collections of permissions.

It must allow:

- Developer-defined default bundles.
- Administrator-created custom bundles.
- Assignment of bundles to multiple roles and groups.
- Controlled updates.
- Visibility into affected roles and groups before changes.
- Application-defined restrictions on customization.
- Auditing of bundle creation, modification, assignment and deletion.

### 3.8 Separation of duties

**AUTH-026 — Separation of duties**

The platform must support:

- **Static separation of duties:** Prevent conflicting roles from being assigned simultaneously.
- **Dynamic separation of duties:** Prevent conflicting actions within a transaction or workflow, even if the user holds both roles.

Examples include preventing a requester from approving their own request or a transaction creator from approving that transaction.

Applications define their own conflicting responsibilities and actions.

### 3.9 Administration and governance

**AUTH-027 — Authorization administration interface**

Kibootia must provide a built-in administration interface for managing authorization configuration.

It should cover users, groups, roles, permissions, policies, assignments and relationships.

**AUTH-028 — Administrator hierarchy**

The platform must support different administrator levels:

- Platform administrators.
- Tenant administrators, when multi-tenancy is enabled.
- Application-defined administrators.

Administrative authority must be scoped appropriately.

**AUTH-029 — Approval of sensitive changes**

Applications must be able to configure approval requirements for sensitive authorization changes.

Examples include granting privileged roles or modifying critical security policies.

**AUTH-030 — Configuration lifecycle**

Authorization configuration must support:

- Draft changes.
- Validation before publication.
- Publishing and activation.
- Version history.
- Rollback to previous versions.

Changes must be auditable.

### 3.10 Security and enforcement

**AUTH-031 — Backend enforcement**

Every protected backend operation must perform authorization enforcement.

Frontend visibility and permission checks are supplementary user-experience features, not security controls.

**AUTH-032 — Decision caching**

The platform must support authorization decision caching where appropriate.

Caching must include reliable invalidation when relevant roles, policies, relationships, assignments or attributes change.

**AUTH-033 — Immediate revocation**

Permission reductions and revocations must take effect immediately by default.

The platform must avoid stale authorization decisions that preserve revoked access.

**AUTH-034 — Consistent authorization interface**

Applications must have a consistent way to request authorization decisions across APIs and backend services.

The consuming application should not need to depend directly on the underlying authorization engine.

**AUTH-035 — Service-to-service authorization**

The platform must support service-to-service authorization through explicit service identities and narrowly scoped permissions.

### 3.11 Developer experience and integrations

**AUTH-036 — Layer-level enforcement**

Authorization must be enforceable at API, service and resource levels.

Applications should have consistent authorization behaviour across these layers.

**AUTH-037 — Developer APIs and SDKs**

Kibootia must provide stable APIs and SDKs for authorization checks and authorization administration.

Spring Boot integration is the initial priority, while preserving the possibility of integration with other clients and technologies.

**AUTH-038 — Custom authorization definitions**

Applications must be able to define custom resource types, actions and attributes without modifying Kibootia's core implementation.

These definitions remain subject to developer-defined extension boundaries.

**AUTH-039 — Identity-provider independence**

The authorization platform must not be tightly coupled to Keycloak.

Keycloak is a supported identity provider, but the platform must preserve the possibility of using alternative identity providers.

Identity and authentication remain separate from application authorization.

**AUTH-040 — Modular authorization capabilities**

Applications must be able to enable or disable supported authorization capabilities according to their requirements.

A small application must not be forced to configure unnecessary enterprise features.

## 4. Responsibility boundaries

The following responsibilities must remain clearly separated.

| Responsibility | Owner |
|---|---|
| User authentication and identity verification | Identity provider |
| Application authorization model | Kibootia and consuming application |
| Role, permission and policy administration | Kibootia |
| Authorization decision evaluation | Kibootia authorization platform |
| Enforcement of protected operations | Consuming application using Kibootia |
| Business rules and validation | Consuming application |
| Workflow state and lifecycle | Consuming application |
| Actual approval workflow execution | Consuming application |
| Authorization audit records | Kibootia |
| Application business audit records | Consuming application |

## 5. Scope classification

### Initial scope

The initial authorization platform should establish:

- Flexible authorization model definition.
- RBAC, ABAC, ReBAC and scoped authorization capabilities.
- Roles, groups, principal types and assignments.
- Contextual and resource-specific access.
- Policy evaluation and conflict resolution.
- Time-bound access and delegation.
- Permission inheritance and bundles.
- Separation of duties.
- Authorization administration.
- Backend enforcement.
- Decision and administrative auditing.
- Developer APIs and Spring Boot integration.
- Identity-provider independence.

### Optional capabilities

These must be supported by the platform but should not be mandatory for every application:

- Multi-tenancy.
- Organizational structures.
- Nested groups.
- Rule-based group membership.
- External group synchronization.
- Advanced inheritance.
- Delegation.
- Field-level authorization.
- Approval requirements for sensitive configuration changes.

### Future scope

- Advanced authorization simulation.
- Policy impact analysis.
- Advanced policy testing and comparison.

## 6. Decisions deliberately left open

The requirements do not yet prescribe implementation technologies or internal architecture.

The following decisions belong to the later architecture and implementation-planning phase:

1. Authorization engine or library selection.
2. Whether to combine multiple authorization engines.
3. Policy language and policy storage.
4. Relationship graph representation and storage.
5. Authorization data model and database schema.
6. Policy evaluation and caching architecture.
7. Distributed enforcement and service communication.
8. Configuration publishing and version management design.
9. Administration interface architecture.
10. Detailed API contracts and SDK design.

Any selected technology must be evaluated against the agreed requirements, including local execution, cloud independence, licensing, maintainability and extensibility.

## 7. Acceptance criteria for the requirements phase

Before implementation planning begins:

- All requirements must have clear and testable meanings.
- Mandatory and optional capabilities must be distinguished.
- Future-scope features must remain separate from initial release requirements.
- Security defaults and responsibility boundaries must be explicit.
- Conflicts and unresolved decisions must be documented.
- Architecture and technology choices must be traceable to the requirements they satisfy.

---

**End of requirements discovery draft.**