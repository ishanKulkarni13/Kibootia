# Authorization: Conditions and Resource Access

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** action-specific checks are verified; list filtering exists in a limited form (all, own or none); the rest is planned. Per-requirement status: [traceability](traceability.md).

Part of the [authorization requirements](README.md).

## Attributes, state and workflow context

### AUTH-006 Attribute and condition-based authorization

**Status:** Approved

Policies can be based on application-defined attributes and conditions. Possible attributes: user, group, resource, organizational, assignment and workflow attributes; request context; time-related and environmental attributes.

Examples: a user belongs to a department; a resource belongs to a department; an assignment is currently valid; a resource has a particular workflow status; a request comes from an allowed environment.

Kibootia does not hardcode application-specific attribute names or business meanings. Applications define which attributes are available and trusted for authorization.

### AUTH-015 State-dependent authorization

**Status:** Approved

A decision can consider the current state of an application resource. Evaluation can use the current resource state, the requested action, resource ownership, principal-resource relationships, reviewer assignments, workflow context, assignment validity and application-provided attributes.

The consuming application defines valid states and transitions and maintains its business lifecycle.

### AUTH-011 Workflow-aware authorization

**Status:** Approved

A decision can depend on application workflow context. Examples:

- only an eligible reviewer can approve a resource in a particular state;
- an author may edit a rejected record;
- a resource may need a specific approval responsibility before an action is allowed;
- a previously approved revision may no longer be eligible for another operation.

Kibootia provides the authorization primitives for these cases. The consuming application owns workflow definitions, state transitions, business validation, workflow progression, notifications and its business approval history.

### AUTH-043 Application-provided workflow revision context

**Status:** Proposed

The system should be able to evaluate revision and approval context supplied by the application. Examples: the current resource revision, a reviewer assignment, an approval status, whether an approval belongs to the current revision, whether an earlier authorization context still applies.

The consuming application owns revision creation, approval invalidation, approval history, notifications and workflow transitions.

> **Review note on AUTH-006, 011, 015 and 043:** these four describe one mechanism from different angles: the application hands the decision point a set of named, typed facts about the resource and the request, and rules test those facts. Recommended: design and deliver them as one capability with one attribute registry, so that "state", "workflow context" and "revision context" are simply attributes the application declares and supplies.
>
> **Owner decision (2026-11-11):** conditions on grants are **named conditions** written by developers in Java (see AUTH-002). Both reference applications fit this: Timetia's "theory lecture or lab and tutorial lecture" and indent-edi's "the indent is a draft" are one named condition each, over an attribute the application supplies.

## Actions and resources

### AUTH-010 Action-specific authorization

**Status:** Approved

Authorization is evaluated for the exact action requested. Permission to view a resource does not imply permission to modify, delete, approve, reject, export or do anything else to it.

Applications define the actions of each resource type. Examples: create, view, list, edit, delete, submit, approve, reject, revert, export, print, assign, delegate, manage. Applications may define more.

### AUTH-014 Independent resource and field-level access

**Status:** Approved

Authorization is independent for each of:

- discovering resources and listing resources;
- viewing an individual resource;
- accessing sensitive fields;
- reading related resources;
- creating and modifying resources;
- modifying specific fields;
- performing workflow actions;
- accessing history and audit records;
- exporting and downloading data.

Permission to view a resource does not imply permission to view every field or related resource. Applications can enforce restrictions on sensitive information.

### AUTH-051 Collection-level authorization

**Status:** Approved (stated in the drafts as part of AUTH-014)

Authorization applies to collections of resources. A principal's list and search results respect the applicable rules, so that an inaccessible resource is not exposed through a list or a search. Filtering can be based on ownership, department, organizational scope, relationships, attributes, role assignments and application-defined conditions.

A consuming application does not rely on frontend filtering alone to protect inaccessible records.

> **Review note:** this is the requirement with the largest effect on the engine choice. The rule already in force is that lists are filtered inside the database query, so that paging and totals are correct ([ADR 0044](../../../decisions/0044-authorization-enforcement-conventions.md)). An engine that can only answer "may this user see this one record" cannot satisfy that without loading every row. See draft [ADR 0056](../../../decisions/0056-authorization-evaluation-engine.md).

## Operations on many resources and on data leaving the system

### AUTH-048 Bulk operation authorization

**Status:** Proposed

The system should support authorization for operations on several resources. Examples: bulk export, bulk deletion, bulk modification, batch assignment, and bulk approval where permitted.

A bulk operation does not bypass the restrictions that apply to each resource. The consuming application defines whether every selected resource must be authorized, whether unauthorized resources are excluded, whether partial execution is permitted, or whether the whole operation fails when any selected resource is inaccessible. Execution and transaction behavior remain application responsibilities.

### AUTH-049 Export and print authorization

**Status:** Proposed

The system should support separate authorization for exporting, downloading and printing. Permission to view a resource does not grant permission to export or print it.

Applications can define different rules for individual record export, multiple-record export, collection export, filtered export, printing, downloading, and the inclusion of sensitive fields in exported data. The authorization system gives the decisions; the consuming application produces the output.
