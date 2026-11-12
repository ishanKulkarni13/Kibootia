# Authorization: Model, Principals and Decision Rules

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned for most of it; role-based access with code-defined permissions and runtime-editable roles is verified. Per-requirement status: [traceability](traceability.md).

Part of the [authorization requirements](README.md).

## AUTH-001 Extensible authorization model definition

**Status:** Approved

Each consuming application can define its own authorization model. Developers can define:

- resource types and the actions each supports;
- resource attributes and principal attributes;
- relationship types;
- default roles, default permission definitions, default policies and default constraints;
- the authorization scopes the application supports;
- permission inheritance rules;
- the boundaries of administrator customization.

The model is independent of any application domain. An application introduces its own definitions without editing the foundation's authorization code. Model changes are validated, versioned, auditable and recoverable.

## AUTH-002 Controlled runtime extensibility

**Status:** Approved

Authorized administrators can configure the authorization elements that developers expose, without a code change or a redeployment. Examples: creating application-specific roles, changing a role's permissions, creating permission bundles, configuring supported access policies, defining supported relationships, configuring scopes and inheritance, managing assignments, and extending permitted attributes.

An application determines which capabilities are fixed, configurable or extensible. Administrators cannot introduce arbitrary executable business operations through authorization configuration. Every administrator-defined extension stays within the application-defined security boundaries.

> **Owner decision (2026-11-11):** administrators do not write conditions. Developers write each condition in Java and give it a name, such as "theory lectures only"; administrators attach named conditions to grants from the list the application exposes. No expression language ships now; one is added later only if a real need appears. "No arbitrary executable business operations" therefore holds by construction: an administrator can only choose among conditions the developer wrote.

## AUTH-003 Multiple authorization models

**Status:** Approved

The system supports:

- **Role-based access control (RBAC):** authorization from roles assigned to principals or groups.
- **Attribute-based access control (ABAC):** authorization from attributes of principals, resources, actions and the request context.
- **Relationship-based access control (ReBAC):** authorization from relationships between principals, groups and resources.
- **Scope-based authorization:** authorization restricted to a defined organizational, application or resource scope.

An application can use one model alone, combine several, evaluate several conditions in one decision, and enable or disable models. The system does not assume that role-based access alone is enough.

## AUTH-018 Multiple principal types

**Status:** Approved

The system supports these principal types:

1. individual users;
2. groups of users;
3. roles assigned to groups;
4. nested groups;
5. service accounts and automated system identities.

A decision can evaluate permissions granted directly to a user or inherited through group membership and role assignments. Assignments to any principal type carry scope, validity, revocation and audit information.

**Nested groups.** Nested membership is supported. The system prevents circular group relationships and ambiguous or unsafe inheritance.

**Service accounts.** A service account is a distinct identity with its own lifecycle and its own assignments. It does not impersonate an ordinary user to perform system operations.

## AUTH-019 Flexible group membership management

**Status:** Approved

Three membership mechanisms are supported, usable alone or together:

- **Manual:** authorized administrators add or remove principals.
- **Rule-based:** membership follows application-defined attributes and conditions.
- **External synchronization:** membership is synchronized from an identity provider or directory.

The system defines how conflicts are handled when manually managed and externally synchronized memberships differ (OQ-55). Membership changes are auditable and are reflected consistently in authorization decisions.

## AUTH-020 Authorization conflict resolution

**Status:** Approved

Conflict resolution is consistent:

- The default is deny.
- An explicit deny takes precedence over an applicable allow.
- A permission for one action is never inferred for an unrelated action.
- When a decision cannot be evaluated reliably, the system fails securely.

An application can define which policy combinations are permitted within the supported security model.

> **Review note:** today nothing can express an explicit deny; every rule is an allow and absence means deny. "Deny takes precedence" therefore needs a deny rule to exist first. The recommended reading is: deny rules are an optional capability an application can switch on, and when they exist they always win (draft [ADR 0055](../../../decisions/0055-authorization-decision-contract.md)).
