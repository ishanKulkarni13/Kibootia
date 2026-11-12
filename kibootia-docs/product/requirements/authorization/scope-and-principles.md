# Authorization: Scope and Principles

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (scope statement). Per-requirement status: [traceability](traceability.md).

Part of the [authorization requirements](README.md).

## Purpose

Kibootia is a reusable application foundation, not a framework for one business domain. Its authorization system must serve applications with very different organizational structures, resource models, responsibilities and security needs, without each application building its own authorization infrastructure.

The system must give a consistent foundation for defining, configuring, evaluating, administering and auditing access permissions. Application developers define their own authorization requirements without editing the foundation's authorization code. Authorized administrators manage the supported configuration at runtime, without a code change or a redeployment.

## Why

A simple application needs a few roles. An enterprise application may need department-specific access, organizational hierarchies, resource ownership, several responsibilities at once, relationship-based permissions, temporary assignments, delegated authority, attribute-dependent and workflow-state-dependent permissions, field-level restrictions, multi-reviewer authorization, approval of sensitive permission changes, and detailed auditing. Building these separately in every application duplicates logic, makes enforcement inconsistent and opens security holes.

## Objectives

1. One authorization interface.
2. Several authorization models, usable alone or together.
3. Applications define their own resources, actions, attributes, relationships, roles and policies.
4. Controlled customization by administrators.
5. Contextual and scoped permissions.
6. Consistent enforcement across protected backend operations.
7. Reliable auditing and configuration governance.
8. Independence from a particular identity provider or authorization engine.
9. Applications enable only the capabilities they need.
10. No domain-specific business rule is imposed on an application.

## Reference applications

The requirements are checked against two applications, **Timetia** (the main reference) and **indent-edi**. They validate the design; they do not limit it, and neither is part of Kibootia. No integration of either with Kibootia is planned (owner decision, 2026-11-11), and indent-edi is being rebuilt from scratch with the same features. Their roles, resources, scopes and rules, with the sources they come from, are in [reference scenarios](reference-scenarios.md).

**Timetia** is an academic scheduling application. Its lecture-rescheduling module has seven roles (Super Admin, Super HOD, HOD, Faculty, class representative, batch representative, Student), department, division and batch scopes, relationships such as "teaches this lecture" and "requested reviewer of this ticket", and per-ticket audit visibility. It shows the need for scoped, relationship-based and state-dependent authorization with records that lie in several scopes.

**indent-edi** is an organizational indent and inventory application. It has a provisional permission matrix of five roles, seven actions and the scopes none, own, department and all, with status rules kept in the application. It shows the need for ownership, department scope, a separate export permission and collection filtering.

## In scope

Authorization model definition; role-based, attribute-based, relationship-based and scope-based access control; resource and action definitions; contextual role assignments; organizational and resource relationships; hierarchical authorization; groups and nested groups; service-account authorization; delegation; temporary permissions; permission inheritance; permission bundles; workflow-aware authorization; reviewer eligibility and approval-related primitives; resource-level, field-level and collection-level authorization; conflict resolution; separation of duties; authorization administration; configuration lifecycle management; authorization auditing; backend enforcement; developer-facing integration interfaces; identity-provider independence; optional multi-tenancy; modular activation of capabilities.

## Out of scope

The authorization system does not:

- define an application's business workflows or the valid lifecycle of its resources;
- perform business validation or domain calculations;
- decide whether a business transaction is financially or operationally valid;
- own application notifications or application data;
- replace an application's domain services;
- implement business operations through authorization policies;
- require every application to use the same organizational structure.

## Responsibility boundary

The authorization system decides whether a principal is permitted to perform an operation. The consuming application decides whether that operation is valid under its business rules. This distinction is mandatory.

| Responsibility | Owner |
|---|---|
| User authentication and identity verification | Identity provider |
| Application authorization model | Kibootia and the consuming application |
| Role, permission and policy administration | Kibootia |
| Authorization decision evaluation | Kibootia |
| Enforcement of protected operations | The consuming application, using Kibootia |
| Business rules and validation | Consuming application |
| Workflow state and lifecycle | Consuming application |
| Approval workflow execution | Consuming application |
| Authorization audit records | Kibootia |
| Business audit records | Consuming application |

Examples:

- In Timetia, Kibootia decides whether a faculty member may approve a rescheduling request. Timetia decides whether the lecture exchange is valid, whether there is a timetable conflict, and whether the ticket has expired.
- In indent-edi, Kibootia decides whether a user may submit an indent or export a collection of indents. indent-edi validates the indent's contents, stock rules, item quantities and submission requirements.

## Terms

These are the terms as the requirements use them. The design vocabulary, which fixes how they relate in code and data, is decided in draft [ADR 0054](../../../decisions/0054-authorization-domain-model.md).

| Term | Meaning |
|---|---|
| Principal | An identity requesting or receiving authorization |
| User | An individual human identity |
| Group | A collection of principals |
| Role | A named collection of permissions or responsibilities |
| Permission | Authorization to perform a particular operation |
| Resource | An application-defined entity being accessed |
| Action | An operation that can be performed on a resource |
| Attribute | A property used during authorization evaluation |
| Relationship | A defined association between principals, groups or resources |
| Scope | The context or boundary within which an authorization applies |
| Policy | A rule governing whether access should be permitted |
| Assignment | A grant of a role or responsibility to a principal or group |
| Delegation | Temporary or controlled transfer of authority |
| Authorization decision | The result of evaluating an access request |
| Tenant | An optional organizational isolation boundary |
| Identity provider | A system responsible for authentication and identity management |
| Workflow context | Application-provided information relevant to an authorization decision |
| Separation of duties | Restrictions preventing incompatible responsibilities from being held or exercised together |

## Design principles

1. **Domain independence.** Kibootia does not assume an academic, commercial, financial, inventory or organizational application. Applications define their own resource types, actions, attributes, relationships, scopes and policies.
2. **Configurable model.** Developers define the capabilities an application supports. Administrators configure the options that the application exposes.
3. **Default deny.** Access is granted only when an applicable rule explicitly permits the operation.
4. **Least privilege.** Principals receive only the permissions their responsibilities need.
5. **Explicit security boundaries.** Administrator configuration never bypasses a developer-defined security constraint.
6. **Backend enforcement.** Trusted backend components enforce authorization. Frontend visibility controls are not security enforcement.
7. **Contextual authorization.** A decision can consider the resource, action, principal, scope, relationships, attributes and application context.
8. **Application ownership of business logic.** Kibootia provides authorization capabilities, not application workflows.
9. **Identity-provider independence.** The authorization system is independent of the identity provider that authenticates.
10. **Modular capabilities.** An application is not forced to enable enterprise capabilities it does not need.

## Architectural decisions deliberately left open

The requirements do not select an authorization engine, a policy language, a relationship-graph implementation, a persistence strategy or schema, a caching or cache-invalidation mechanism, a distributed authorization strategy, an API design, the Keycloak integration details, the administration interface implementation, or code-level module boundaries. These are decided in ADRs, and any selected technology is evaluated against these requirements, including local execution, cloud independence, licensing, maintainability and extensibility.
