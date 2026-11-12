# Kibootia — Software Requirements Specification (SRS)
## Enterprise Authorization and Access Control System

**Document Type:** New Software Requirements Specification  
**Document Status:** Requirements Draft — For Review and Approval  
**Document Version:** 1.0  
**Document Classification:** New Standalone Project Document  
**Project:** Kibootia  
**Subsystem:** Authorization and Access Control  
**Primary Reference Applications:** Timetia and indent-edi  
**Document Purpose:** Define the functional and non-functional requirements for a reusable, extensible, domain-agnostic authorization system within Kibootia.

---

# 1. Document Control

## 1.1 Document Identity

This document introduces a new, standalone Software Requirements Specification for the Kibootia Authorization and Access Control System.

It is intended to be added to the existing Kibootia documentation repository as a new document.

This document does not replace, overwrite, or supersede existing Kibootia documentation. It establishes a dedicated requirements baseline for the authorization subsystem.

## 1.2 Document Status

This document is a requirements draft.

Its purpose is to establish what the authorization subsystem must support before architectural design, technology selection, detailed implementation planning, or development begins.

Requirements marked as proposed refinements or requiring confirmation must not automatically be treated as formally approved product decisions.

## 1.3 Intended Audience

This document is intended for:

- Kibootia maintainers and developers.
- Application developers integrating Kibootia into consuming applications.
- Technical architects.
- Security engineers.
- Product and system designers.
- Administrators responsible for configuring authorization.
- Reviewers evaluating the suitability of Kibootia for different applications.

## 1.4 Requirement Identification

Every functional requirement has a stable identifier in the following format:

`AUTH-XXX`

These identifiers should remain stable even if the document's sections are reorganized.

Requirement status is distinguished as follows:

- **Established:** A capability or direction already agreed upon during requirements discussions.
- **Proposed:** A recommended refinement introduced to improve completeness.
- **Future:** A capability explicitly deferred from the initial implementation scope.
- **Open:** A requirement or product decision that requires further confirmation.

---

# 2. Introduction

## 2.1 Purpose

The purpose of this document is to define the complete requirements for an enterprise-grade authorization and access control subsystem within Kibootia.

Kibootia is intended to serve as a reusable application foundation rather than a framework tied to one particular business domain.

Consequently, its authorization subsystem must be sufficiently flexible to support applications with substantially different organizational structures, resource models, user responsibilities, business processes, and security requirements.

The authorization subsystem must provide a consistent foundation for defining, configuring, evaluating, administering, and auditing access permissions across consuming applications.

It must allow application developers to define their domain-specific authorization requirements without requiring modifications to Kibootia's core authorization functionality.

It must also provide controlled runtime configurability so that authorized administrators can manage supported authorization configurations without requiring application code changes or redeployment.

## 2.2 Background and Motivation

Different applications have different access-control requirements.

A basic application might only need a few roles, such as administrator, manager, and user.

An enterprise application may require:

- Department-specific access.
- Organizational hierarchies.
- Resource ownership.
- Multiple simultaneous responsibilities.
- Relationship-based permissions.
- Temporary assignments.
- Delegated authority.
- Attribute-dependent permissions.
- Workflow-state-dependent permissions.
- Field-level restrictions.
- Multi-reviewer authorization.
- Administrative approval of sensitive permission changes.
- Detailed authorization auditing.

Implementing these capabilities separately in every application leads to duplicated logic, inconsistent enforcement, security vulnerabilities, and higher maintenance costs.

Kibootia should provide reusable authorization capabilities that consuming applications can configure according to their requirements.

## 2.3 Product Vision

The authorization subsystem should function as a domain-independent authorization foundation capable of supporting both simple and complex applications.

Its primary objectives are:

1. Provide a unified authorization interface.
2. Support multiple authorization models.
3. Allow applications to define their own resources, actions, attributes, relationships, roles, and policies.
4. Support controlled administrator customization.
5. Support contextual and scoped permissions.
6. Enforce authorization consistently across protected backend operations.
7. Provide reliable auditing and configuration governance.
8. Remain independent of a particular identity provider or authorization engine.
9. Allow applications to enable only the capabilities they need.
10. Avoid imposing domain-specific business rules on consuming applications.

## 2.4 Reference Applications

The requirements are validated against two distinct applications.

### 2.4.1 Timetia

Timetia is an academic scheduling and coordination application.

Its authorization requirements include:

- Super administrators and application administrators.
- Department-level administrative authority.
- Faculty responsibilities.
- Class representatives and batch representatives.
- Student access restrictions.
- Faculty-to-course and faculty-to-lecture assignments.
- Division and batch relationships.
- Temporary responsibilities.
- Ticket and lecture-rescheduling permissions.
- Reviewer eligibility.
- Multi-reviewer approval scenarios.
- Workflow-state-dependent actions.
- Restricted visibility of tickets and audit histories.

Timetia demonstrates the need for contextual, relationship-based, hierarchical, and workflow-aware authorization.

### 2.4.2 indent-edi

indent-edi is an organizational indent and inventory-related application.

Its documented functionality includes:

- Indent creation.
- Item management.
- Current-stock display.
- Saving indents as drafts.
- Submitting indents.
- Indent listing and filtering.
- Viewing indent details.
- Editing draft indents.
- Deleting eligible records.
- Printing.
- Excel exports.
- Department and indent-type master data.

Its current implementation uses local JSON data as a demonstration data source. Its README identifies authentication and permission enforcement as unimplemented.

The final authorization matrix and approval workflow have not been established by the current application documentation.

Therefore, the examples in this SRS describe possible authorization capabilities for indent-edi, not confirmed final business rules.

indent-edi demonstrates the need for resource ownership, department-scoped access, action-specific permissions, collection-level authorization, and export restrictions.

---

# 3. Scope

## 3.1 In Scope

The authorization subsystem shall provide the following capabilities:

- Authorization model definition.
- Role-based access control.
- Attribute-based access control.
- Relationship-based access control.
- Scope-based authorization.
- Resource and action definitions.
- Contextual role assignments.
- Organizational and resource relationships.
- Hierarchical authorization.
- Group and nested-group support.
- Service-account authorization.
- Delegation.
- Temporary permissions.
- Permission inheritance.
- Permission bundles.
- Workflow-aware authorization.
- Reviewer eligibility and approval-related authorization primitives.
- Resource-level and field-level authorization.
- Collection-level authorization.
- Conflict resolution.
- Separation of duties.
- Authorization administration.
- Configuration lifecycle management.
- Authorization auditing.
- Backend enforcement.
- Developer-facing integration interfaces.
- Identity-provider independence.
- Optional multi-tenancy.
- Modular capability activation.

## 3.2 Out of Scope

The following responsibilities do not belong to the authorization subsystem itself:

- Defining application-specific business workflows.
- Defining the valid business lifecycle of application resources.
- Performing business validation.
- Implementing domain-specific calculations.
- Determining whether a business transaction is financially or operationally valid.
- Owning application-specific notifications.
- Managing application-specific data.
- Replacing the consuming application's domain services.
- Implementing arbitrary business operations through authorization policies.
- Requiring every consuming application to use the same organizational structure.

The authorization subsystem determines whether a principal is permitted to perform an operation.

The consuming application remains responsible for determining whether that operation is valid according to its business rules.

## 3.3 Fundamental Responsibility Boundary

The distinction between authorization and business logic is mandatory.

For example:

In Timetia, Kibootia may determine whether a faculty member is authorized to approve a rescheduling request.

Timetia remains responsible for determining whether the requested lecture exchange is valid, whether there is a timetable conflict, and whether the ticket has expired.

In indent-edi, Kibootia may determine whether a user is authorized to submit an indent or export a collection of indents.

indent-edi remains responsible for validating the indent's contents, stock-related rules, item quantities, and application-specific submission requirements.

---

# 4. Terminology

| Term | Meaning |
|---|---|
| Principal | An identity requesting or receiving authorization. |
| User | An individual human identity. |
| Group | A collection of principals. |
| Role | A named collection of permissions or responsibilities. |
| Permission | Authorization to perform a particular operation. |
| Resource | An application-defined entity being accessed. |
| Action | An operation that can be performed on a resource. |
| Attribute | A property used during authorization evaluation. |
| Relationship | A defined association between principals, groups, or resources. |
| Scope | The context or boundary within which an authorization applies. |
| Policy | A rule governing whether access should be permitted. |
| Assignment | A grant of a role or responsibility to a principal or group. |
| Delegation | Temporary or controlled transfer of authority. |
| Authorization decision | The result of evaluating an access request. |
| Tenant | An optional organizational isolation boundary. |
| Identity provider | A system responsible for authentication and identity management. |
| Workflow context | Application-provided information relevant to an authorization decision. |
| Separation of duties | Restrictions preventing incompatible responsibilities from being held or exercised together. |

---

# 5. Core Design Principles

The following principles shall guide the design of the authorization subsystem.

## 5.1 Domain Independence

Kibootia must not assume that every application is an academic, commercial, financial, inventory, or organizational management application.

Applications must be able to define their own resource types, actions, attributes, relationships, scopes, and policies.

## 5.2 Configurable Authorization Model

The subsystem must support both developer-defined foundations and controlled administrator customization.

Developers define the capabilities an application supports.

Administrators configure the permitted authorization options exposed by that application.

## 5.3 Default Deny

Access must not be granted unless an applicable authorization rule explicitly permits the requested operation.

## 5.4 Least Privilege

Principals should receive only the permissions required for their responsibilities.

## 5.5 Explicit Security Boundaries

Administrator configuration must never bypass developer-defined security constraints.

## 5.6 Backend Enforcement

Authorization must be enforced by trusted backend components.

Frontend visibility controls must not be treated as security enforcement.

## 5.7 Contextual Authorization

Authorization decisions must be able to consider the resource, action, principal, scope, relationships, attributes, and relevant application context.

## 5.8 Application Ownership of Business Logic

Kibootia provides authorization capabilities, not application-specific business workflows.

## 5.9 Identity-Provider Independence

The authorization subsystem must remain independent of the identity provider responsible for authentication.

## 5.10 Modular Capabilities

Applications must not be forced to enable enterprise capabilities that they do not need.

---

# 6. Authorization Model and Extensibility

## 6.1 AUTH-001 — Extensible Authorization Model Definition

**Status:** Established

The subsystem shall allow each consuming application to define its own authorization model.

Developers shall be able to define:

- Resource types.
- Supported actions.
- Resource attributes.
- Principal attributes.
- Relationship types.
- Default roles.
- Default permission definitions.
- Default policies.
- Default constraints.
- Supported authorization scopes.
- Permission inheritance rules.
- Administrator customization boundaries.

The model must be independent of Kibootia's current application domain.

Applications must be able to introduce domain-specific authorization definitions without modifying Kibootia's core functionality.

## 6.2 AUTH-002 — Controlled Runtime Extensibility

**Status:** Established

The subsystem shall support controlled runtime customization of developer-exposed authorization capabilities.

Authorized administrators shall be able to configure supported authorization elements without requiring application code changes or redeployment.

Examples include:

- Creating application-specific roles.
- Modifying role permissions.
- Creating permission bundles.
- Configuring supported access policies.
- Defining supported relationships.
- Configuring scopes.
- Configuring inheritance.
- Managing assignments.
- Extending permitted attributes.

Applications must be able to determine which capabilities are fixed, configurable, or extensible.

Administrators must not be allowed to introduce arbitrary executable business operations through authorization configuration.

All administrator-defined extensions must remain within application-defined security boundaries.

## 6.3 AUTH-003 — Support for Multiple Authorization Models

**Status:** Established

The subsystem shall support the following authorization models:

### Role-Based Access Control (RBAC)

Authorization based on roles assigned to principals or groups.

### Attribute-Based Access Control (ABAC)

Authorization based on attributes associated with principals, resources, actions, and request context.

### Relationship-Based Access Control (ReBAC)

Authorization based on relationships between principals, groups, and resources.

### Scope-Based Authorization

Authorization restricted to a defined organizational, application, or resource scope.

Applications shall be able to:

- Use one model independently.
- Combine multiple models.
- Evaluate multiple authorization conditions in a single decision.
- Enable or disable supported models according to their requirements.

The subsystem must not assume that RBAC alone is sufficient for every application.

---

# 7. Principals, Users, and Groups

## 7.1 AUTH-018 — Multiple Principal Types

**Status:** Established

The subsystem shall support the following principal types:

1. Individual users.
2. Groups of users.
3. Roles assigned to groups.
4. Nested groups.
5. Service accounts and automated system identities.

The authorization model must be capable of evaluating permissions granted directly to a user or inherited through applicable group membership and role assignments.

### Nested Groups

Nested group membership must be supported.

The subsystem must prevent circular group relationships and ambiguous or unsafe inheritance.

### Service Accounts

Service accounts must be treated as distinct identities.

They must have their own lifecycle and authorization assignments.

Service accounts must not require impersonation of ordinary human users to perform authorized system operations.

## 7.2 AUTH-019 — Flexible Group Membership Management

**Status:** Established

The subsystem shall support three group membership mechanisms.

### Manual Membership

Authorized administrators can manually add or remove principals.

### Rule-Based Membership

Membership may be determined using application-defined attributes and conditions.

### External Synchronization

Membership may be synchronized from external identity providers or directory systems.

Applications must be able to use these mechanisms independently or combine them.

The subsystem must define how conflicts are handled when manually managed and externally synchronized memberships differ.

All relevant membership changes must be auditable.

---

# 8. Roles, Permissions, and Assignments

## 8.1 AUTH-004 — Contextual and Scoped Assignments

**Status:** Established

The subsystem shall support role and responsibility assignments within specific contexts.

Examples include:

- Institution.
- Organization.
- Department.
- Faculty.
- Course.
- Class.
- Batch.
- Project.
- Individual resource.

A principal may hold multiple roles simultaneously, with each assignment having a different scope.

The subsystem must support:

- Global role assignments.
- Organization-scoped assignments.
- Department-scoped assignments.
- Resource-scoped assignments.
- Multiple simultaneous assignments.
- Assignment start dates.
- Assignment expiry.
- Manual revocation.
- Suspension.
- Assignment provenance.

An assignment at one scope must not automatically grant access to unrelated scopes.

## 8.2 AUTH-008 — Multiple Simultaneous and Contextual Roles

**Status:** Established

A principal may hold several roles or responsibilities simultaneously.

For example, a person may:

- Be a faculty member in one department.
- Hold an administrative responsibility in another department.
- Be assigned to review a particular resource.
- Have a temporary responsibility for a specific project.

Authorization decisions must consider the applicable assignment and its scope rather than treating a user's highest-level role as their complete authority.

## 8.3 AUTH-025 — Reusable Permission Bundles

**Status:** Established

The subsystem shall support reusable permission bundles.

Developers may define default bundles.

Authorized administrators may create and manage additional bundles within permitted extension boundaries.

Bundles must be assignable to multiple roles or other supported authorization structures.

The subsystem should support:

- Reuse of permission definitions.
- Controlled modification.
- Visibility into affected roles and principals.
- Audit of changes.
- Identification of inherited permissions.
- Restrictions on modifying developer-protected bundles.

Changes to a shared permission bundle must not silently introduce unintended access.

---

# 9. Relationships and Organizational Structures

## 9.1 AUTH-005 — Relationship-Based Authorization

**Status:** Established

The subsystem shall allow applications to define relationships between principals, groups, and resources.

Examples include:

- Faculty assigned to a course.
- Student enrolled in a course.
- Representative assigned to a batch.
- Coordinator responsible for a department.
- User responsible for a project.
- Employee assigned to a particular resource.

Authorization decisions must be able to evaluate relevant relationships.

Applications must be able to define their own relationship types and semantics.

## 9.2 AUTH-007 — Hierarchical and Inherited Authorization Scopes

**Status:** Established

The subsystem shall support hierarchical organizational and resource scopes.

Examples include:

- Institution.
- Department.
- Division.
- Batch.
- Class.

A higher-level administrator may be granted authority over lower-level scopes.

However, inheritance must be configurable.

The subsystem must support:

- Permission propagation.
- Restrictions on propagation.
- Explicit inheritance boundaries.
- Exceptions to inheritance.
- Scope-specific administrative authority.
- Explanation of inherited permissions.

A relationship or organizational hierarchy must not automatically grant permissions unless the application's authorization model explicitly allows it.

## 9.3 AUTH-009 — Resource-Specific Relationships and Temporary Responsibilities

**Status:** Established

The subsystem shall support relationships attached to individual resources.

Such relationships must not be restricted to organizational roles.

Examples include:

- A faculty member assigned to review a particular ticket.
- A user assigned to manage an individual project.
- A person temporarily acting for another user.
- A reviewer associated with a particular application record.

These relationships may be time-bound.

They must be independently manageable and auditable.

## 9.4 AUTH-016 — Flexible Organizational and Resource Relationships

**Status:** Intended direction; explicit confirmation required

The subsystem should support both:

1. Hierarchical organizational structures.
2. Multiple overlapping relationships between principals, groups, and resources.

Applications must define their own organizational structures and relationship semantics.

The subsystem must not assume that every relationship follows a single tree structure.

Permission inheritance across relationships must be configurable.

---

# 10. Attribute-Based and Contextual Authorization

## 10.1 AUTH-006 — Attribute and Condition-Based Authorization

**Status:** Established

The subsystem shall support policies based on application-defined attributes and conditions.

Potential attributes include:

- User attributes.
- Group attributes.
- Resource attributes.
- Organizational attributes.
- Assignment attributes.
- Workflow attributes.
- Request context.
- Time-related attributes.
- Environmental attributes.

Examples include:

- A user belongs to a particular department.
- A resource belongs to a particular department.
- An assignment is currently valid.
- A resource has a particular workflow status.
- A request originates from an allowed environment.

Kibootia must not hardcode application-specific attribute names or business meanings.

Applications must define which attributes are available and trusted for authorization.

## 10.2 AUTH-015 — State-Dependent Authorization

**Status:** Established

Authorization decisions shall be able to consider the current state of an application resource.

The subsystem must support evaluation using:

- Current resource state.
- Requested action.
- Resource ownership.
- Principal-resource relationships.
- Reviewer assignments.
- Relevant workflow context.
- Assignment validity.
- Application-provided attributes.

The consuming application remains responsible for defining valid state transitions and maintaining its business lifecycle.

## 10.3 AUTH-011 — Workflow-Aware Authorization

**Status:** Established

The subsystem shall support authorization decisions that depend on application workflow context.

Examples include:

- Only an eligible reviewer can approve a resource in a particular state.
- An author may be permitted to edit a rejected record.
- A resource may require a specific approval responsibility before an action is allowed.
- A previously approved revision may no longer be eligible for another operation.

Kibootia shall provide authorization primitives for these scenarios.

The consuming application shall own:

- Workflow definitions.
- State transitions.
- Business validation.
- Workflow progression.
- Notifications.
- Business-specific approval history.

---

# 11. Action-Specific and Resource-Level Authorization

## 11.1 AUTH-010 — Action-Specific Authorization

**Status:** Established

Authorization must be evaluated for the exact action being requested.

Permission to view a resource must not automatically imply permission to modify, delete, approve, reject, export, or perform other operations on it.

Applications must be able to define individual actions for each resource type.

Examples include:

- Create.
- View.
- List.
- Edit.
- Delete.
- Submit.
- Approve.
- Reject.
- Revert.
- Export.
- Print.
- Assign.
- Delegate.
- Manage.

Applications may define additional actions.

## 11.2 AUTH-014 — Independent Resource and Field-Level Access

**Status:** Established

The subsystem shall support independent authorization for:

- Discovering resources.
- Listing resources.
- Viewing individual resources.
- Accessing sensitive fields.
- Reading related resources.
- Creating resources.
- Modifying resources.
- Modifying specific fields.
- Performing workflow actions.
- Accessing history.
- Accessing audit records.
- Exporting data.
- Downloading data.

Permission to view a resource must not imply permission to view every field or related resource.

Applications must be able to enforce restrictions on sensitive information.

The subsystem must support collection-level filtering so that inaccessible resources are not exposed through list or search operations.

## 11.3 Collection-Level Authorization

**Status:** Established through AUTH-014

The subsystem must support authorization over resource collections.

A principal's list and search results must respect the applicable authorization rules.

This includes filtering based on:

- Ownership.
- Department.
- Organizational scope.
- Relationships.
- Attributes.
- Role assignments.
- Application-defined conditions.

A consuming application must not rely only on frontend filtering to protect inaccessible records.

---

# 12. Delegation and Temporary Authority

## 12.1 AUTH-012 — Delegation and Acting Authority

**Status:** Established

The subsystem shall support controlled delegation of authority.

Examples include:

- A department administrator delegating a responsibility.
- A department head temporarily acting for an absent person.
- A coordinator transferring a defined responsibility.
- A reviewer assigning an authorized substitute where permitted.

Delegation must define:

- Delegator.
- Delegate.
- Scope.
- Permitted actions.
- Validity period.
- Applicable constraints.
- Revocation conditions.

Delegated authority must not exceed the delegator's own authority.

Delegation must be auditable.

## 12.2 AUTH-021 — Time-Bound Authorization

**Status:** Established

The subsystem shall support time-bound assignments and permissions.

It must support:

- Start date and time.
- Expiry date and time.
- Automatic expiration.
- Manual revocation.
- Authorized renewal.
- Authorized extension.
- Scope-specific validity.
- Correct time-zone handling.

Expired assignments must no longer authorize access.

Changes to validity must be auditable.

---

# 13. Hierarchy, Inheritance, and Multi-Tenancy

## 13.1 AUTH-024 — Configurable Permission Inheritance

**Status:** Established

The subsystem shall support configurable permission inheritance across organizational scopes and supported resource relationships.

Applications must define:

- Which permissions may be inherited.
- Which relationships support inheritance.
- Whether inheritance is automatic or explicitly configured.
- Which scopes may inherit from parent scopes.
- Where inheritance must stop.
- Which restrictions override inherited access.

The subsystem should provide an explanation of the origin of inherited permissions.

## 13.2 AUTH-017 — Optional Multi-Tenancy and Organizational Isolation

**Status:** Established

The subsystem shall support both single-tenant and multi-tenant applications.

However, organizational and tenant structures must remain optional.

A consuming application must be able to operate without introducing an organization or tenant concept.

When multi-tenancy is enabled, the subsystem must support:

- Tenant-scoped authorization.
- Tenant-specific users and groups.
- Tenant-specific assignments.
- Tenant-specific relationships.
- Tenant-specific policies.
- Tenant-specific administrative authority.
- Isolation of tenant-owned resources.
- Explicit platform-level cross-tenant authority.

A single-tenant application must not be forced to configure unnecessary tenant structures.

Applications must define their own organizational structures.

The existence of users, roles, permissions, relationships, and authorization evaluation is a core platform capability.

Organizations, tenants, departments, and hierarchies are optional application capabilities.

---

# 14. Conflict Resolution and Separation of Duties

## 14.1 AUTH-020 — Authorization Conflict Resolution

**Status:** Established

The subsystem shall provide consistent conflict-resolution behavior.

The default authorization behavior shall be deny.

Explicit denial must take precedence over an applicable allow decision.

The subsystem must not infer permissions for unrelated actions.

If an authorization decision cannot be evaluated reliably, the system must fail securely.

Applications must be able to define permitted policy combinations within the supported security model.

## 14.2 AUTH-026 — Separation of Duties

**Status:** Established

The subsystem shall support static and dynamic separation of duties.

### Static Separation of Duties

Prevents incompatible roles or responsibilities from being assigned to the same principal where the application defines such restrictions.

### Dynamic Separation of Duties

Prevents incompatible actions from being performed together or within a defined operational context.

Applications must be able to define:

- Conflicting roles.
- Conflicting responsibilities.
- Restricted action combinations.
- Applicable scopes.
- Relevant contexts.
- Permitted exceptions.

The system must support controlled application-defined exceptions where business requirements permit them.

## 14.3 AUTH-041 — Application-Defined Exceptions to Separation of Duties

**Status:** Proposed refinement

Applications should be able to define explicit exceptions to separation-of-duties restrictions for specific actions, resources, and contexts.

For example, an application may allow a user to perform a limited action on a resource they created while still requiring a separate authority for more sensitive operations.

Exceptions must be:

- Explicitly configured.
- Limited to a defined scope.
- Restricted to permitted actions.
- Auditable.
- Prevented from weakening unrelated restrictions.

This capability is particularly relevant when applications have different approval requirements for different types of operations.

---

# 15. Approval-Related Authorization Capabilities

## 15.1 AUTH-013 — Multi-Reviewer and Approval Authorization

**Status:** Established

The subsystem shall support authorization requirements for applications that involve multiple reviewers or approval responsibilities.

Applications must be able to define authorization conditions for:

- Eligible reviewers.
- Required reviewers.
- Optional reviewers.
- All-reviewer approval.
- Any-reviewer approval.
- Quorum-based authorization.
- Rejection authority.
- Escalation authority.
- Self-approval restrictions.
- Context-specific reviewer permissions.

Kibootia provides authorization primitives.

The consuming application's workflow engine owns the actual approval process.

## 15.2 AUTH-042 — Reviewer Eligibility and Optional/Required Status

**Status:** Proposed refinement

Applications should be able to define reviewer eligibility using:

- Roles.
- Attributes.
- Relationships.
- Scope.
- Resource assignment.
- Workflow context.

Applications should be able to distinguish mandatory and optional reviewer responsibilities.

The authorization subsystem must be capable of evaluating whether a particular principal is authorized to perform a reviewer action.

The consuming application remains responsible for selecting reviewers, tracking progress, and deciding when the workflow is complete.

## 15.3 AUTH-043 — Application-Provided Workflow Revision Context

**Status:** Proposed refinement

The subsystem should be capable of evaluating application-provided revision and approval context.

Examples include:

- Current resource revision.
- Reviewer assignment.
- Approval status.
- Whether an approval is associated with the current revision.
- Whether a previous authorization context remains applicable.

The consuming application owns revision creation, approval invalidation, approval history, notifications, and workflow transitions.

---

# 16. Audit and Accountability

## 16.1 AUTH-022 — Authorization Decision Auditing

**Status:** Established

The subsystem shall provide auditing of authorization decisions.

Relevant audit information should include:

- Principal identity.
- Requested action.
- Resource identity.
- Authorization outcome.
- Decision reason.
- Relevant policy context.
- Timestamp.
- Correlation identifier.

Audit records must be protected against unauthorized modification.

Access to audit records must itself be controlled.

Retention policies must be configurable.

The subsystem must avoid recording unnecessary sensitive information.

## 16.2 AUTH-044 — Independent Authorization of Audit-History Visibility

**Status:** Proposed refinement

Access to audit histories must be independently authorized.

Permission to access a resource must not automatically grant access to its audit history.

Applications must be able to define audit-history visibility using:

- Principal identity.
- Role.
- Scope.
- Resource relationships.
- Application-defined attributes.
- Other supported authorization conditions.

Access to audit records must itself be auditable.

## 16.3 Administrative Configuration Auditing

Administrative authorization changes must be auditable.

Relevant changes include:

- Role creation.
- Role modification.
- Permission assignment.
- Permission revocation.
- Policy modification.
- Relationship modification.
- Group membership changes.
- Delegation.
- Assignment extension.
- Configuration publication.
- Configuration rollback.

The audit system must allow administrators to identify who performed a change, what was changed, and when it occurred.

---

# 17. Authorization Administration and Governance

## 17.1 AUTH-027 — Authorization Administration Interface

**Status:** Established

Kibootia shall provide an administrative interface for managing authorization configurations.

Depending on the capabilities enabled by the consuming application, administrators should be able to manage:

- Users.
- Groups.
- Roles.
- Permissions.
- Permission bundles.
- Policies.
- Assignments.
- Relationships.
- Organizational scopes.
- Delegations.
- Time-bound responsibilities.
- Relevant authorization settings.

The interface must respect the administrator's own authorization boundaries.

## 17.2 AUTH-028 — Administrator Hierarchy

**Status:** Established

The subsystem shall support different administrative authority levels.

Examples include:

- Platform administrators.
- Tenant administrators, where multi-tenancy is enabled.
- Application administrators.
- Scope-specific administrators.

Applications must define the authority and boundaries of their administrative roles.

A tenant administrator must not automatically receive platform-level authority.

An administrator must not be able to grant themselves permissions outside their permitted administrative boundary.

## 17.3 AUTH-029 — Approval of Sensitive Changes

**Status:** Established

The subsystem shall support application-configurable approval requirements for sensitive authorization changes.

Examples include:

- Granting privileged roles.
- Modifying critical authorization policies.
- Changing administrative authority.
- Creating cross-tenant permissions.
- Modifying sensitive access boundaries.

Applications must be able to decide which changes require approval and what approval process applies.

Kibootia must provide the relevant authorization capabilities without assuming a single universal approval workflow.

## 17.4 AUTH-030 — Configuration Lifecycle Management

**Status:** Established

Authorization configuration must have a controlled lifecycle.

The subsystem shall support:

1. Draft configuration.
2. Configuration validation.
3. Publication.
4. Activation.
5. Version history.
6. Rollback.

Invalid configuration must not become active.

Changes must be auditable.

The system must ensure that configuration activation does not bypass developer-defined security constraints.

---

# 18. Developer Integration and Backend Enforcement

## 18.1 AUTH-031 — Backend Enforcement

**Status:** Established

All protected backend operations must enforce authorization.

The frontend must not be treated as the security boundary.

The subsystem must ensure that unauthorized requests cannot bypass access restrictions by directly invoking backend endpoints.

## 18.2 AUTH-034 — Consistent Authorization Interface

**Status:** Established

Consuming applications must use a consistent authorization interface.

Application developers must not need to depend directly on the internal implementation of the selected authorization engine.

The interface must support the capabilities exposed by the consuming application's authorization model.

## 18.3 AUTH-036 — Layer-Level Enforcement

**Status:** Established

Authorization enforcement must be consistent across relevant application layers.

The subsystem must support enforcement at:

- API boundaries.
- Application/service operations.
- Resource access boundaries.

The exact enforcement mechanisms may vary according to the consuming application's architecture.

However, authorization must not be enforced exclusively in the presentation layer.

## 18.4 AUTH-037 — Developer APIs and SDKs

**Status:** Established

The subsystem shall provide stable developer-facing APIs and SDKs for authorization evaluation and administration.

The initial integration priority is the Spring Boot ecosystem.

Other clients may be supported as the platform evolves.

Developer-facing capabilities should include:

- Authorization checks.
- Resource and action definitions.
- Attribute and relationship integration.
- Scope-aware checks.
- Assignment management.
- Policy configuration.
- Authorization decision handling.

The API must remain independent of the underlying authorization engine.

## 18.5 AUTH-038 — Custom Authorization Definitions

**Status:** Established

Consuming applications must be able to define custom authorization resources, actions, and attributes without modifying Kibootia's core.

Such definitions must be subject to application-defined extension boundaries and validation.

## 18.6 AUTH-035 — Service-to-Service Authorization

**Status:** Established

The subsystem shall support authorization for service accounts and automated system identities.

Service identities must be able to receive narrowly scoped permissions.

The subsystem must support authorization checks for machine-to-machine operations.

Service identities must not automatically inherit the permissions of human users.

## 18.7 AUTH-039 — Identity-Provider Independence

**Status:** Established

The authorization subsystem must not be tightly coupled to a particular identity provider.

Keycloak is a supported identity-provider direction.

Alternative identity providers must remain possible.

Authentication and identity management must remain conceptually separate from application authorization.

The authorization subsystem must be capable of consuming trusted identity information from supported identity providers.

## 18.8 AUTH-040 — Modular Authorization Capabilities

**Status:** Established

The subsystem shall support modular activation of authorization capabilities.

Applications must be able to enable or disable optional features according to their requirements.

A simple application must not be forced to configure:

- Multi-tenancy.
- Nested groups.
- Complex relationship models.
- Multi-reviewer approval.
- Delegation.
- Advanced policy customization.
- Other enterprise-specific capabilities it does not need.

The system must provide a usable baseline while allowing applications to adopt more advanced capabilities when necessary.

---

# 19. Additional Requirements for Resource Ownership and Data Operations

The following refinements are recommended based on the second reference application, indent-edi.

They are intended to make the platform's general-purpose resource authorization requirements more explicit.

## 19.1 AUTH-046 — Resource Ownership-Based Authorization

**Status:** Proposed refinement

The subsystem should support authorization based on resource ownership.

Applications must be able to define ownership semantics for their resources.

Examples include:

- A user accessing an indent they created.
- A project member accessing a project they own.
- A user managing records assigned to them.

Ownership must be treated as an application-defined relationship or attribute, not a universal assumption that automatically grants every possible permission.

Applications must be able to distinguish between:

- Resource creator.
- Current owner.
- Assigned responsible person.
- Administrative authority.
- Other application-defined relationships.

These relationships may have different authorization consequences.

## 19.2 AUTH-047 — Resource Ownership and Scope Transfer

**Status:** Proposed refinement

The subsystem should support controlled changes to resource ownership and authorization scope.

Examples include:

- Transferring a resource to another department.
- Changing its responsible owner.
- Reassigning a resource to another organizational unit.

Such changes must be authorized.

The subsystem must ensure that relevant authorization decisions reflect the updated ownership and scope.

Changes must be auditable.

Applications must define the business conditions under which ownership or scope transfers are permitted.

## 19.3 AUTH-048 — Bulk Operation Authorization

**Status:** Proposed refinement

The subsystem should support authorization for operations involving multiple resources.

Examples include:

- Bulk export.
- Bulk deletion.
- Bulk modification.
- Batch assignment.
- Bulk approval, where permitted.

A bulk operation must not bypass the authorization restrictions applicable to individual resources.

The consuming application must be able to define whether:

- Every selected resource must be authorized.
- Unauthorized resources are excluded.
- Partial execution is permitted.
- The entire operation must fail if any selected resource is inaccessible.

The actual execution and transaction behavior remain application responsibilities.

## 19.4 AUTH-049 — Export and Print Authorization

**Status:** Proposed refinement

The subsystem should support separate authorization for exporting, downloading, and printing resources.

Permission to view a resource must not automatically grant permission to export or print it.

Applications must be able to define different access rules for:

- Individual record export.
- Multiple-record export.
- Collection export.
- Filtered export.
- Printing.
- Downloading.
- Sensitive-field inclusion in exported data.

The authorization subsystem provides the relevant decisions.

The consuming application remains responsible for producing the export or print output.

---

# 20. Authorization Simulation and Future Capabilities

## 20.1 AUTH-023 — Authorization Simulation and Policy Impact Analysis

**Status:** Future scope

The subsystem should eventually support authorization simulation and policy impact analysis.

Potential capabilities include:

- Previewing access granted by a proposed policy.
- Identifying principals affected by a configuration change.
- Comparing current and proposed authorization configurations.
- Identifying unintended permission escalation.
- Detecting conflicting policies.
- Identifying unreachable or ineffective policies.
- Simulating authorization decisions without applying them.

This capability is not mandatory for the initial implementation.

It should remain part of the long-term authorization roadmap.

---

# 21. Proposed Refinement Summary

The following requirements were introduced or strengthened to improve coverage across both reference applications.

| ID | Requirement | Status |
|---|---|---|
| AUTH-041 | Application-defined exceptions to separation of duties | Proposed |
| AUTH-042 | Reviewer eligibility and required/optional status | Proposed |
| AUTH-043 | Application-provided workflow revision context | Proposed |
| AUTH-044 | Independent authorization of audit-history visibility | Proposed |
| AUTH-045 | Separation of authorization validity and business deadlines | Proposed |
| AUTH-046 | Resource ownership-based authorization | Proposed |
| AUTH-047 | Resource ownership and scope transfer | Proposed |
| AUTH-048 | Bulk operation authorization | Proposed |
| AUTH-049 | Export and print authorization | Proposed |

These requirements are included to make the specification more complete.

Their inclusion in this document does not automatically mean they have been formally approved.

---

# 22. AUTH-045 — Authorization Validity Versus Business Deadlines

**Status:** Proposed refinement

The subsystem must distinguish between authorization validity and application-specific business deadlines.

Authorization validity concerns whether a principal's role, assignment, delegation, or permission is currently valid.

Business deadlines concern whether an application-specific operation is still valid according to its domain rules.

For example:

- A temporary administrator assignment may expire at a specified date and time.
- A Timetia rescheduling ticket may expire when the earliest affected lecture begins.
- An indent submission deadline may be determined by the consuming application.

Kibootia provides time-aware authorization.

The consuming application owns its business deadlines and resource lifecycle expiration.

The authorization subsystem must not assume that every business deadline is an authorization expiry.

---

# 23. Non-Functional Requirements

## 23.1 Security

The subsystem must:

- Apply default-deny behavior.
- Prevent unauthorized access.
- Protect administrative operations.
- Enforce developer-defined security boundaries.
- Prevent privilege escalation through configuration.
- Ensure that service identities have controlled permissions.
- Protect audit records.
- Fail securely when authorization evaluation cannot be completed reliably.

## 23.2 Reliability

Authorization decisions must be reliable and consistent.

The subsystem must avoid granting access because of incomplete policy evaluation, missing relationship information, or unavailable trusted context.

The exact availability and consistency targets remain open for architectural and operational planning.

## 23.3 Performance

Authorization evaluation must be efficient enough to support normal application request volumes.

The design must consider:

- Role lookup.
- Relationship evaluation.
- Attribute evaluation.
- Policy evaluation.
- Collection-level filtering.
- Nested group membership.
- Permission inheritance.
- Caching.

Specific latency and throughput targets are not established by this requirements draft.

They must be defined during non-functional requirements validation and architecture design.

## 23.4 Scalability

The subsystem should be capable of supporting applications with different scales and complexity.

It must not assume that every application has the same number of users, resources, relationships, roles, or policies.

## 23.5 Maintainability

The subsystem should be maintainable independently of individual consuming applications.

Application-specific authorization definitions should not require frequent changes to Kibootia's core.

## 23.6 Extensibility

New resource types, actions, attributes, relationship types, policies, and supported capabilities should be introducible through defined extension mechanisms.

Extensions must remain subject to validation and security constraints.

## 23.7 Auditability

Security-sensitive operations and authorization configuration changes must produce suitable audit records.

## 23.8 Interoperability

The subsystem should remain independent of a single identity provider, authorization engine, or application domain.

## 23.9 Deployability

The broader Kibootia foundation is intended to support locally runnable and cloud-agnostic deployments.

The authorization subsystem should not impose an unnecessary dependency on a particular cloud provider.

Specific deployment and infrastructure decisions are deferred to architecture design.

---

# 24. Reference Application Validation

This section evaluates whether the requirements can support both Timetia and indent-edi without introducing application-specific logic into Kibootia.

## 24.1 Timetia Validation Scenarios

### Scenario 1 — Department-Level Administration

A department administrator must be able to manage permitted resources within their department.

Relevant requirements:

- AUTH-004.
- AUTH-007.
- AUTH-010.
- AUTH-024.
- AUTH-028.

### Scenario 2 — Faculty Assignment

A faculty member must be able to access resources associated with their teaching responsibilities.

Relevant requirements:

- AUTH-005.
- AUTH-006.
- AUTH-008.
- AUTH-009.

### Scenario 3 — Class and Batch Representatives

A representative must be able to perform permitted actions within the applicable division or batch.

Relevant requirements:

- AUTH-004.
- AUTH-005.
- AUTH-007.
- AUTH-010.

### Scenario 4 — Temporary Acting Authority

An authorized person may temporarily act on behalf of another person within defined boundaries.

Relevant requirements:

- AUTH-012.
- AUTH-021.
- AUTH-022.

### Scenario 5 — Reviewer Authorization

Only eligible reviewers may perform permitted reviewer actions.

Relevant requirements:

- AUTH-011.
- AUTH-013.
- AUTH-042.
- AUTH-043.

### Scenario 6 — Restricted Ticket and Audit Visibility

A student or other principal must not automatically receive access to tickets or their audit histories merely because they can access timetable information.

Relevant requirements:

- AUTH-014.
- AUTH-022.
- AUTH-044.

### Scenario 7 — Resource Revision

A revised ticket may require previous approvals to be reconsidered.

Relevant requirements:

- AUTH-011.
- AUTH-015.
- AUTH-043.

Timetia remains responsible for implementing the ticket lifecycle, revision history, and approval invalidation rules.

## 24.2 indent-edi Validation Scenarios

The following are validation scenarios derived from the documented functionality of indent-edi. They are not final application permission decisions.

### Scenario 1 — Indent Creation

A permitted user creates an indent.

Relevant requirements:

- AUTH-010.
- AUTH-014.
- AUTH-031.

### Scenario 2 — Draft Editing

A permitted user modifies an eligible draft indent.

Relevant requirements:

- AUTH-010.
- AUTH-015.
- AUTH-046.

### Scenario 3 — Department-Scoped Visibility

A user accesses only the indents permitted by their department, ownership, role, or other application-defined relationships.

Relevant requirements:

- AUTH-004.
- AUTH-005.
- AUTH-006.
- AUTH-014.
- AUTH-046.

### Scenario 4 — Submission

A user performs the submit operation only when authorized.

Relevant requirements:

- AUTH-010.
- AUTH-011.
- AUTH-015.

### Scenario 5 — Deletion

A user deletes a resource only when the relevant authorization policy permits it.

Relevant requirements:

- AUTH-010.
- AUTH-015.

The rule that deletion is allowed only while an indent is in Draft is currently a placeholder in the prototype documentation. It must be confirmed by the indent-edi application.

### Scenario 6 — Excel Export

A user exports only the records and fields they are authorized to export.

Relevant requirements:

- AUTH-014.
- AUTH-048.
- AUTH-049.

### Scenario 7 — Ownership or Department Transfer

An authorized operation changes a resource's owner or department.

Relevant requirements:

- AUTH-005.
- AUTH-006.
- AUTH-047.

This scenario is a proposed capability and must be validated against the actual business requirements.

### Scenario 8 — Approval Workflow

If indent-edi introduces an approval workflow, Kibootia must be able to authorize the corresponding reviewer and approval actions.

Relevant requirements:

- AUTH-011.
- AUTH-013.
- AUTH-042.

The final approval process, number of approvers, and approval hierarchy remain unconfirmed.

## 24.3 Cross-Application Validation Conclusion

Both applications can be represented through the same generic authorization concepts.

| Authorization capability | Timetia | indent-edi |
|---|---|---|
| Users and groups | Required | Required |
| Roles and permissions | Required | Required |
| Contextual assignments | Important | Useful |
| Departmental scopes | Important | Important |
| Resource relationships | Important | Useful |
| Attribute-based policies | Important | Useful |
| Workflow-aware authorization | Important | Useful |
| Resource ownership | Useful | Important |
| Field-level authorization | Important | Important |
| Collection-level authorization | Important | Important |
| Multi-reviewer authorization | Important | Not yet established |
| Temporary delegation | Important | Application-dependent |
| Export authorization | Application-dependent | Important |
| Audit authorization | Important | Important |
| Multi-tenancy | Optional | Optional |

The two applications demonstrate that Kibootia must not be designed around a single authorization model or organizational structure.

They provide complementary validation cases for a reusable authorization foundation.

---

# 25. Acceptance Criteria

The authorization subsystem shall be considered functionally aligned with this requirements specification when the following conditions are satisfied.

## 25.1 Authorization Model

- Applications can define custom resource types.
- Applications can define custom actions.
- Applications can define attributes and relationships.
- RBAC, ABAC, ReBAC, and scope-based authorization are supported.
- Multiple authorization models can participate in a decision.

## 25.2 Extensibility

- Developers can define application-specific authorization foundations.
- Administrators can configure exposed extension points.
- Unauthorized configuration changes are prevented.
- Invalid configurations cannot be activated.
- Configuration changes are auditable.

## 25.3 Principals and Assignments

- Individual users are supported.
- Groups and nested groups are supported.
- Service accounts are supported.
- Multiple roles can be assigned simultaneously.
- Assignments can be scoped.
- Assignments can expire or be revoked.
- Delegation is constrained by the delegator's authority.

## 25.4 Resource Authorization

- Different actions can have different permissions.
- Resource-level authorization is supported.
- Field-level authorization is supported.
- Collection-level filtering is supported.
- Export and bulk operations can be authorized separately.

## 25.5 Workflow and Context

- Authorization decisions can evaluate resource state.
- Authorization decisions can evaluate application-provided context.
- Reviewer eligibility can be evaluated.
- Application-defined separation-of-duties constraints can be enforced.
- The consuming application retains ownership of its business workflow.

## 25.6 Governance and Auditing

- Administrative operations are protected.
- Authorization decisions can be audited.
- Configuration changes can be audited.
- Audit-history access is independently controllable.
- Configuration versioning and rollback are supported.

## 25.7 Integration

- Backend operations enforce authorization.
- Developers have a consistent authorization interface.
- Identity-provider integration remains independent of the authorization model.
- Applications can enable only the capabilities they require.

## 25.8 Reference Application Suitability

- Timetia can express academic roles, scopes, relationships, reviewer eligibility, and resource-level authorization.
- indent-edi can express resource ownership, department-scoped access, draft editing, submission, deletion, and export authorization.
- Neither application requires its domain-specific business logic to be implemented inside Kibootia's authorization core.

---

# 26. Requirement Classification and Scope Planning

The following classification provides an initial planning direction. It is not a final release commitment.

## 26.1 Core Authorization Foundation

The initial foundation should prioritize:

- AUTH-001: Extensible authorization model.
- AUTH-002: Controlled runtime extensibility.
- AUTH-003: Multiple authorization models.
- AUTH-004: Contextual assignments.
- AUTH-005: Relationship-based authorization.
- AUTH-006: Attribute-based authorization.
- AUTH-010: Action-specific authorization.
- AUTH-014: Resource and field-level authorization.
- AUTH-018: Multiple principal types.
- AUTH-020: Conflict resolution.
- AUTH-022: Authorization auditing.
- AUTH-031: Backend enforcement.
- AUTH-034: Consistent authorization interface.
- AUTH-037: Developer APIs and SDKs.
- AUTH-038: Custom authorization definitions.
- AUTH-039: Identity-provider independence.
- AUTH-040: Modular capabilities.

## 26.2 Advanced Authorization Capabilities

These capabilities should be planned as part of the broader system:

- AUTH-007: Hierarchical scopes.
- AUTH-008: Multiple simultaneous roles.
- AUTH-009: Resource-specific relationships.
- AUTH-011: Workflow-aware authorization.
- AUTH-012: Delegation.
- AUTH-013: Multi-reviewer authorization.
- AUTH-015: State-dependent authorization.
- AUTH-016: Flexible organizational relationships.
- AUTH-017: Optional multi-tenancy.
- AUTH-019: Flexible group membership.
- AUTH-021: Time-bound authorization.
- AUTH-024: Permission inheritance.
- AUTH-025: Permission bundles.
- AUTH-026: Separation of duties.
- AUTH-027: Administration interface.
- AUTH-028: Administrator hierarchy.
- AUTH-029: Approval of sensitive changes.
- AUTH-030: Configuration lifecycle.
- AUTH-032: Decision caching.
- AUTH-033: Immediate revocation.
- AUTH-035: Service-to-service authorization.
- AUTH-036: Layer-level enforcement.

## 26.3 Proposed Refinements Requiring Approval

- AUTH-041: Application-defined exceptions to separation of duties.
- AUTH-042: Reviewer eligibility and optional/required status.
- AUTH-043: Workflow revision context.
- AUTH-044: Independent audit-history authorization.
- AUTH-045: Authorization validity versus business deadlines.
- AUTH-046: Resource ownership-based authorization.
- AUTH-047: Resource ownership and scope transfer.
- AUTH-048: Bulk operation authorization.
- AUTH-049: Export and print authorization.

## 26.4 Future Scope

- AUTH-023: Authorization simulation and policy impact analysis.

The final release boundaries must be established after requirements review and architecture planning.

---

# 27. Open Questions and Decisions Requiring Confirmation

The following questions remain intentionally open.

They must be resolved before the corresponding architectural or implementation decisions are finalized.

## 27.1 Authorization Model

1. Should the system support both hierarchical organizational structures and overlapping relationship graphs in the initial implementation?
2. Which authorization capabilities should be mandatory in the initial foundation?
3. Which capabilities should be optional modules?
4. What level of administrator customization should be exposed by default?

## 27.2 Identity and Principal Management

5. Which identity-provider integrations must be supported initially?
6. How should externally synchronized group memberships interact with manual memberships?
7. What is the expected lifecycle for service accounts?
8. How should disabled or deleted identities affect existing authorization assignments?

## 27.3 Multi-Tenancy

9. Should the initial release implement full tenant isolation, or establish the capability boundaries for later implementation?
10. What cross-tenant administrative capabilities are permitted?
11. How should tenant-level configuration interact with application-level configuration?

## 27.4 Authorization Configuration

12. Which changes should require administrative approval?
13. Should configuration publication require one administrator or multiple administrators?
14. What rollback guarantees are required?
15. How should policy conflicts be presented to administrators?

## 27.5 Audit and Security

16. What audit retention periods are required?
17. Which audit records must be immutable?
18. What level of authorization decision detail should be stored?
19. What consistency guarantees are required after permission revocation?
20. What should happen when an external authorization dependency is unavailable?

## 27.6 Performance and Scalability

21. What are the expected authorization decision latency targets?
22. What are the expected numbers of users, groups, relationships, and policies?
23. What are the expected collection sizes for filtered authorization queries?
24. What are the acceptable caching and revocation consistency trade-offs?

## 27.7 Reference Application Validation

25. What is the final role and permission matrix for indent-edi?
26. Does indent-edi require department-wise approval?
27. Are there multiple approval levels?
28. Who may edit an indent after submission?
29. Which users may export or print records?
30. Can resource ownership or department assignment change after creation?

These questions must not be answered through assumptions in Kibootia's core requirements.

---

# 28. Architectural Decisions Explicitly Deferred

This document specifies requirements, not implementation architecture.

The following decisions are deliberately deferred:

- Selection of an authorization engine.
- Selection of a policy language.
- Selection of a relationship graph implementation.
- Selection of a persistence strategy.
- Database schema.
- Authorization configuration storage.
- Policy evaluation architecture.
- Relationship storage architecture.
- Caching implementation.
- Cache invalidation mechanism.
- Distributed authorization strategy.
- Authorization API design.
- SDK implementation details.
- Integration details with Keycloak.
- Administrative UI implementation.
- Deployment architecture.
- Module boundaries at code level.
- Internal package structure.
- Infrastructure and observability tooling.

Potential technologies may be evaluated later, including OPA, OpenFGA, Cedar, and Casbin.

No technology is selected or mandated by this SRS.

The architecture must be derived from the approved requirements and evaluated against the needs of both reference applications.

---

# 29. Traceability and Change Management

Every requirement in this document has a stable identifier.

Future revisions should:

- Preserve existing requirement identifiers.
- Record newly introduced requirements.
- Identify requirements that have been modified.
- Identify requirements that have been removed.
- Record the reason for significant changes.
- Distinguish approved requirements from proposals.
- Maintain traceability between requirements, architecture decisions, implementation, and tests.

When a consuming application reveals a new authorization requirement, the team must first determine whether it represents:

1. A generic authorization capability that belongs in Kibootia.
2. An application-specific authorization definition.
3. An application-specific business rule.
4. A feature that should remain optional.
5. A future capability outside the current implementation scope.

This distinction is essential to preserving Kibootia's domain independence.

---

# 30. Final Requirements Statement

Kibootia's Authorization and Access Control System shall provide a reusable, extensible, domain-agnostic authorization foundation for applications with different organizational structures, resource models, and operational workflows.

It shall support RBAC, ABAC, ReBAC, and scope-based authorization, with controlled extensibility for application developers and authorized administrators.

It shall support users, groups, service identities, contextual assignments, relationships, resource ownership, time-bound responsibilities, delegation, permission inheritance, field-level authorization, collection-level filtering, workflow-aware decisions, and administrative governance.

It shall enforce authorization at trusted backend boundaries, provide consistent developer-facing integration interfaces, and maintain suitable auditing and configuration controls.

It shall remain independent of a particular identity provider, authorization engine, cloud provider, or business domain.

Timetia and indent-edi shall serve as complementary validation applications.

Timetia validates the platform's ability to support relationship-heavy, contextual, hierarchical, and workflow-aware authorization.

indent-edi validates the platform's ability to support resource ownership, department-scoped access, action-specific permissions, collection filtering, and controlled data operations.

Neither application shall require its domain-specific business logic to be embedded within Kibootia's authorization core.

**The objective is not to build an authorization system for Timetia or indent-edi. The objective is to build a reusable authorization foundation capable of supporting both applications, as well as future applications whose requirements have not yet been identified.**

---

**End of Document**

**Document:** Kibootia — Enterprise Authorization and Access Control System SRS  
**Version:** 1.0  
**Status:** New standalone requirements draft  
**Approval:** Pending review and confirmation