# Authorization: Assignments, Relationships and Scopes

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned, except tenant-wide role assignment, ownership of a record and the tenant-ready schema, which are verified. Per-requirement status: [traceability](traceability.md).

Part of the [authorization requirements](README.md).

## Roles and assignments

### AUTH-004 Contextual and scoped assignments

**Status:** Approved

Roles and responsibilities can be assigned within a specific context. Example contexts: institution, organization, department, faculty, course, class, batch, project, individual resource.

A principal can hold several roles at once, each with a different scope. The system supports:

- global, organization-scoped, department-scoped and resource-scoped assignments;
- several simultaneous assignments;
- assignment start dates and expiry;
- manual revocation and suspension;
- assignment provenance (who granted it, when, and through what).

An assignment at one scope does not grant access to an unrelated scope.

### AUTH-008 Multiple simultaneous and contextual roles

**Status:** Approved

A principal can hold several roles or responsibilities at once. For example, one person may be a faculty member in one department, hold an administrative responsibility in another, be assigned to review one resource, and have a temporary responsibility for one project.

A decision considers the applicable assignment and its scope. A user's highest-level role is not treated as their complete authority.

### AUTH-025 Reusable permission bundles

**Status:** Approved

The system supports reusable permission bundles. Developers define default bundles. Authorized administrators create and manage additional bundles within the permitted extension boundaries. A bundle can be assigned to several roles or other supported structures.

The system supports reuse of permission definitions, controlled modification, visibility into the affected roles and principals before a change, audit of creation, modification, assignment and deletion, identification of inherited permissions, and restrictions on modifying developer-protected bundles.

A change to a shared bundle does not silently introduce unintended access.

> **Review note:** today a role is itself a bundle of permissions. A second layer (bundle inside role) adds an indirection that every "why does this user have this permission" explanation must walk. It is worth building only when an application has several roles that share a large common set. Recommended: deliver after scoped assignments and groups, and keep it switchable (AUTH-040).

## Relationships

### AUTH-005 Relationship-based authorization

**Status:** Approved

Applications can define relationships between principals, groups and resources. Examples: faculty assigned to a course, student enrolled in a course, representative assigned to a batch, coordinator responsible for a department, user responsible for a project, employee assigned to a resource.

A decision can evaluate the relevant relationships. Applications define their own relationship types and their meaning.

### AUTH-009 Resource-specific relationships and temporary responsibilities

**Status:** Approved

Relationships can be attached to an individual resource and are not limited to organizational roles. Examples: a faculty member assigned to review one ticket, a user assigned to manage one project, a person temporarily acting for another user, a reviewer associated with one application record.

These relationships may be time-bound. They are managed and audited independently.

### AUTH-016 Flexible organizational and resource relationships

**Status:** Open (the owner's intended direction; explicit confirmation required, OQ-50)

The system should support both hierarchical organizational structures and several overlapping relationships between principals, groups and resources. Applications define their own structures and relationship meanings. The system does not assume that every relationship follows a single tree. Permission inheritance across relationships is configurable.

### AUTH-046 Resource ownership-based authorization

**Status:** Proposed

The system should support authorization based on resource ownership. Applications define what ownership means for their resources. Examples: a user accessing an indent they created, a project member accessing a project they own, a user managing records assigned to them.

Ownership is an application-defined relationship or attribute. It is not a universal rule that grants every permission. Applications can distinguish the resource creator, the current owner, the assigned responsible person, administrative authority and other relationships, and each may have different authorization consequences.

### AUTH-047 Resource ownership and scope transfer

**Status:** Proposed

The system should support controlled changes to a resource's ownership and authorization scope. Examples: transferring a resource to another department, changing its responsible owner, reassigning it to another organizational unit.

Such a change is itself authorized and audited. Decisions made afterwards reflect the new ownership and scope. Applications define the business conditions under which a transfer is permitted.

## Hierarchy and inheritance

### AUTH-007 Hierarchical and inherited authorization scopes

**Status:** Approved

The system supports hierarchical organizational and resource scopes, for example institution, department, division, batch, class. A higher-level administrator may be given authority over lower-level scopes, and inheritance is configurable.

The system supports permission propagation, restrictions on propagation, explicit inheritance boundaries, exceptions to inheritance, scope-specific administrative authority, and an explanation of inherited permissions.

A relationship or hierarchy grants nothing by itself. It grants permissions only where the application's authorization model says so.

> **Evidence from the reference applications:** a record can lie in several scopes at once. A Timetia lecture belongs to a division, a batch and a department, and a shared course to several batches; an indent-edi indent belongs to a department through its header or any of its lines. The model therefore gives a resource a **set** of scope nodes, and a grant at any one of them (or above it, where inheritance is on) can reach it ([ADR 0054](../../../decisions/0054-authorization-domain-model.md)).

### AUTH-024 Configurable permission inheritance

**Status:** Approved

Permission inheritance across organizational scopes and supported resource relationships is configurable. Applications define:

- which permissions may be inherited;
- which relationships support inheritance;
- whether inheritance is automatic or explicitly configured;
- which scopes may inherit from parent scopes;
- where inheritance stops;
- which restrictions override inherited access.

Administrators can see where an inherited permission comes from.

## Tenancy

### AUTH-017 Optional multi-tenancy and organizational isolation

**Status:** Approved

The system supports single-tenant and multi-tenant applications. Organization and tenant structures are optional: an application can run without any organization or tenant concept, and a single-tenant application is not made to configure tenant structures.

When multi-tenancy is enabled, the system supports tenant-scoped authorization; tenant-specific users, groups, assignments, relationships and policies; tenant-specific administrative authority; isolation of tenant-owned resources; and explicit platform-level authority for anything that crosses tenants.

Users, roles, permissions, relationships and decision evaluation are core capabilities. Organizations, tenants, departments and hierarchies are optional capabilities that an application defines for itself.

> **Review note:** the schema is already tenant-ready with one default tenant ([ADR 0028](../../../decisions/0028-tenant-ready-schema.md)). "Tenant" (a hard isolation boundary: data never crosses it) and "organizational scope" (a department inside one tenant: a boundary for permissions, not for data isolation) are different mechanisms and should stay different. Timetia's departments and indent-edi's departments are scopes, not tenants.
