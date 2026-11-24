# 0009 Role-based authorization first, extensible later

- **Type:** adr
- **Module:** platform
- **Status:** deprecated
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** not applicable (a decision record). What exists, and the CI run that verified it, is in [implementation status](../product/roadmap/implementation-status.md).

> **Extended by [ADR 0027](0027-central-authorization-decision-point.md)**, which adds a permission model and a central decision point. This decision still holds. Role data is now stored in Kibootia's database ([ADR 0025](0025-identity-and-authorization-data-split.md)); [ADR 0010](0010-keycloak-realm-roles-as-role-source.md), named below, is superseded.

## Context

Authentication proves who a caller is; applications also need to decide what they may do. Source: project definition, section 6.3.

## Decision

Authorization is enforced in the backend and starts as role-based. The conventions leave room for contextual policies later. A complete ABAC engine is not an initial requirement. Frontend checks are for usability only.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Full ABAC from the start | Excluded as unnecessary complexity at this stage |
| Frontend-only checks | Not a security control |

## Consequences

Contextual policies (ownership, tenant) will need later ADRs. The source of role data is in [ADR 0010](0010-keycloak-realm-roles-as-role-source.md).

## Status

deprecated; was accepted on 2026-10-28. Superseded by [ADR 0053](0053-adopt-authorization-requirements.md). It was extended by [ADR 0027](0027-central-authorization-decision-point.md).

## Related

[Authorization](../architecture/identity/authorization.md).
