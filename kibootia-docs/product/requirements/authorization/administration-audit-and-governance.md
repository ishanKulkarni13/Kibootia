# Authorization: Administration, Audit and Governance

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** user, role and assignment administration with an audit log of administrative changes is verified; decision auditing, the configuration lifecycle, approvals and the administrator hierarchy are planned. Per-requirement status: [traceability](traceability.md).

Part of the [authorization requirements](README.md).

## Audit and accountability

### AUTH-022 Authorization decision auditing

**Status:** Approved

The system audits authorization decisions. A record holds the principal identity, the requested action, the resource identity, the outcome (allow or deny), the decision reason, the relevant policy context, a timestamp, and a correlation identifier that links to the application's request where one exists.

Records are protected against unauthorized modification, access to them is itself controlled, and retention is configurable. The system avoids recording unnecessary sensitive information.

> **Review note:** decisions and administrative changes differ by orders of magnitude in volume. One page view can make a dozen decisions; the existing audit log is written in the same database transaction as the change it describes ([ADR 0046](../../../decisions/0046-audit-log-design.md)), which is right for changes and far too heavy for every decision. Recommended: a separate decision log; by default it records every denial and every allow of an action the application marks as sensitive; recording every allow is a switch. How much detail to keep is OQ-67.

### AUTH-044 Independent authorization of audit-history visibility

**Status:** Proposed

Access to audit histories is authorized independently. Permission to access a resource does not grant access to its audit history.

Applications can define audit-history visibility using principal identity, role, scope, resource relationships, application-defined attributes and other supported conditions. Access to audit records is itself auditable.

### AUTH-050 Administrative configuration auditing

**Status:** Approved (an unnumbered section of the newer draft; part of AUTH-022 in the older one)

Administrative authorization changes are audited, separately from decisions. Covered changes: role creation and modification; permission assignment and revocation; policy modification; relationship modification; group membership changes; delegation; assignment extension; configuration publication; configuration rollback.

Administrators can identify who made a change, what changed and when.

## Administration

### AUTH-027 Authorization administration interface

**Status:** Approved

Kibootia provides an administration interface for authorization configuration. Depending on the capabilities the application enables, administrators can manage users, groups, roles, permissions, permission bundles, policies, assignments, relationships, organizational scopes, delegations, time-bound responsibilities and related settings.

The interface respects the administrator's own authorization boundaries.

### AUTH-028 Administrator hierarchy

**Status:** Approved

The system supports different levels of administrative authority. Examples: platform administrators; tenant administrators, where multi-tenancy is enabled; application administrators; scope-specific administrators.

Applications define the authority and boundaries of their administrative roles. A tenant administrator does not receive platform-level authority automatically. An administrator cannot grant themselves permissions outside their permitted administrative boundary.

> **Owner decision (2026-11-11):** a user may assign only roles whose permissions they hold themselves, as the role editor already requires when it grants permissions to a role. Today role assignment checks only `access.roles.assign` and protects only the administrator role, so a holder of that one permission can take any other existing role ([security review](../../../temp/authorization-security-review.md)). The fix is the first item of milestone 7, with allowed and denied tests.

### AUTH-029 Approval of sensitive changes

**Status:** Approved

Applications can configure approval requirements for sensitive authorization changes. Examples: granting privileged roles, modifying critical authorization policies, changing administrative authority, creating cross-tenant permissions, modifying sensitive access boundaries.

Applications decide which changes need approval and what approval process applies. Kibootia provides the capability without assuming one universal approval workflow.

> **Review note:** this is the one place where the authorization system must own a small workflow of its own (a change waits until a second administrator approves it), because the thing being approved is authorization data. Recommended: keep it to one fixed pattern, a pending change with a configurable number of approvers who must differ from the requester, and do not build a general workflow engine.

### AUTH-030 Configuration lifecycle management

**Status:** Approved

Authorization configuration has a controlled lifecycle: draft, validation, publication, activation, version history, rollback. An invalid configuration never becomes active. Changes are auditable. Activation never bypasses a developer-defined security constraint.

> **Review note:** "configuration" needs a boundary. Two kinds of data exist. **Definitions** (roles and their permissions, bundles, policies, inheritance rules, separation-of-duties rules) change rarely, can break access for many people at once, and benefit from draft, validate, publish and rollback. **Operational data** (who holds which role, group membership, relationships, delegations) changes constantly and must take effect at once (AUTH-033); putting a new staff member's role through draft and publish would be unusable, and rolling back a version must not resurrect a revoked assignment. Recommended: the lifecycle applies to definitions only; operational data is audited and optionally approval-gated (AUTH-029), never versioned.

## Later

### AUTH-023 Authorization simulation and policy impact analysis

**Status:** Deferred

The system should eventually support simulation and impact analysis: previewing the access a proposed policy grants; identifying the principals a change affects; comparing current and proposed configurations; identifying unintended permission escalation; detecting conflicting policies; identifying unreachable or ineffective policies; simulating a decision without applying anything.

This is not required for the initial implementation and stays on the long-term roadmap.
