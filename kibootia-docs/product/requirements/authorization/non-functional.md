# Authorization: Non-Functional Requirements and Acceptance Criteria

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (quality targets and acceptance checks). No numeric target is set yet; see the open questions named below.

Part of the [authorization requirements](README.md).

## Non-functional requirements

| Quality | Requirement |
|---|---|
| Security | Default deny. Unauthorized access is prevented. Administrative operations are protected. Developer-defined security boundaries are enforced. Configuration cannot be used to escalate privilege. Service identities have controlled permissions. Audit records are protected. Evaluation that cannot complete reliably fails securely |
| Reliability | Decisions are reliable and consistent. Access is never granted because policy evaluation was incomplete, relationship information was missing or trusted context was unavailable |
| Performance | Evaluation is efficient enough for normal request volumes. The design considers role lookup, relationship evaluation, attribute evaluation, policy evaluation, collection-level filtering, nested group membership, permission inheritance and caching |
| Scalability | The system supports applications of different size and complexity and does not assume a fixed number of users, resources, relationships, roles or policies |
| Maintainability | The system is maintained independently of any consuming application. Application-specific definitions do not need frequent changes to the foundation |
| Extensibility | New resource types, actions, attributes, relationship types, policies and capabilities are introduced through defined extension mechanisms and stay subject to validation and security constraints |
| Auditability | Security-sensitive operations and configuration changes produce audit records |
| Interoperability | The system is independent of a single identity provider, authorization engine or application domain |
| Deployability | The system runs locally and does not depend on a particular cloud provider. Deployment and infrastructure choices are architecture decisions |

## What is missing: numbers

None of the rows above can be tested as written, because none has a number. Until the owner sets them, the performance, scalability and reliability rows are intentions, not requirements. The missing values are tracked as open questions:

| Missing value | Open question |
|---|---|
| Decision latency target (for example, the 95th percentile of one check) | OQ-70 |
| Expected numbers of users, groups, relationships and policies | OQ-71 |
| Expected collection sizes for filtered list queries | OQ-72 |
| Accepted staleness after a revocation, if any | OQ-68, OQ-73 |
| Audit retention periods and which records must be immutable | OQ-65, OQ-66 |
| Behavior when an external authorization dependency is unavailable | OQ-69 |

**Review note:** a practical way to set the first numbers is to measure what exists. Today a decision costs one indexed query. Record that as the baseline in the first authorization slice and require that each later slice states its cost against it.

## Acceptance criteria

The system is functionally aligned with these requirements when all of the following hold. Each line is checked by automated tests in the phase that delivers it ([authorization roadmap](../../roadmap/authorization-roadmap.md)), and counts only when it is verified in CI ([verification standard](../../../engineering/testing/verification-standard.md)).

### Authorization model

- Applications define custom resource types, actions, attributes and relationships.
- Role-based, attribute-based, relationship-based and scope-based authorization are supported.
- Several models can take part in one decision.

### Extensibility

- Developers define application-specific authorization foundations.
- Administrators configure the exposed extension points.
- Unauthorized configuration changes are prevented.
- An invalid configuration cannot be activated.
- Configuration changes are auditable.

### Principals and assignments

- Individual users, groups, nested groups and service accounts are supported.
- Several roles can be assigned at once.
- Assignments can be scoped, and can expire or be revoked.
- Delegation is limited by the delegator's authority.

### Resource authorization

- Different actions can have different permissions.
- Resource-level and field-level authorization are supported.
- Collection-level filtering is supported.
- Export and bulk operations can be authorized separately.

### Workflow and context

- Decisions can evaluate resource state and application-provided context.
- Reviewer eligibility can be evaluated.
- Application-defined separation-of-duties constraints can be enforced.
- The consuming application keeps ownership of its business workflow.

### Governance and auditing

- Administrative operations are protected.
- Decisions and configuration changes can be audited.
- Audit-history access is independently controllable.
- Configuration versioning and rollback are supported.

### Integration

- Backend operations enforce authorization.
- Developers have one consistent authorization interface.
- Identity-provider integration is independent of the authorization model.
- Applications can enable only the capabilities they require.

### Reference application suitability

- Timetia can express academic roles, scopes, relationships, reviewer eligibility and resource-level authorization.
- indent-edi can express resource ownership, department-scoped access, draft editing, submission, deletion and export authorization.
- Neither application needs its business logic inside Kibootia's authorization code.
