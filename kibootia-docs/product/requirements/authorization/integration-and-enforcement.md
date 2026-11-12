# Authorization: Integration and Enforcement

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** backend enforcement, the single authorization interface and immediate revocation are verified; service identities, caching and capability switches for authorization are planned. Per-requirement status: [traceability](traceability.md).

Part of the [authorization requirements](README.md).

## Enforcement

### AUTH-031 Backend enforcement

**Status:** Approved

Every protected backend operation enforces authorization. The frontend is not the security boundary: its visibility and permission checks are user-experience features. An unauthorized request cannot bypass a restriction by calling a backend endpoint directly.

### AUTH-036 Layer-level enforcement

**Status:** Approved

Enforcement is consistent across application layers. The system supports enforcement at API boundaries, at application or service operations, and at resource access boundaries. The mechanisms may differ with the application's architecture, but authorization is never enforced only in the presentation layer.

### AUTH-032 Decision caching

**Status:** Approved (defined in the older draft; listed without a definition in the newer one)

The system supports caching of authorization decisions where appropriate. A cache has reliable invalidation when relevant roles, policies, relationships, assignments or attributes change.

### AUTH-033 Immediate revocation

**Status:** Approved (defined in the older draft; listed without a definition in the newer one)

Permission reductions and revocations take effect immediately by default. The system avoids stale decisions that preserve revoked access.

> **Review note on AUTH-032 and AUTH-033:** these pull against each other, and the order of priority should be written down: revocation wins. Today there is no cache at all, each decision reads the database, and a test with two application instances shows that a role change applies on the very next request ([ADR 0044](../../../decisions/0044-authorization-enforcement-conventions.md)). That satisfies AUTH-033 fully. Recommended: a cache is added only when measurement shows a need, only in a form that cannot serve a revoked permission (for example, validated against a version number on every use), and the accepted staleness, if any, is a stated number (OQ-68, OQ-73).

## Developer integration

### AUTH-034 Consistent authorization interface

**Status:** Approved

Consuming applications use one consistent authorization interface across APIs and backend services. Application developers do not depend on the internals of the selected authorization engine. The interface supports the capabilities that the application's authorization model exposes.

### AUTH-037 Developer APIs and SDKs

**Status:** Approved

The system provides stable developer-facing APIs and SDKs for authorization evaluation and administration. The first integration priority is the Spring Boot ecosystem; other clients may follow.

Developer-facing capabilities include authorization checks, resource and action definitions, attribute and relationship integration, scope-aware checks, assignment management, policy configuration and decision handling. The API is independent of the underlying engine.

> **Review note:** Kibootia publishes no libraries; an application copies the repository and owns the code ([ADR 0023](../../../decisions/0023-template-fork-adoption-model.md)). Under that model "SDK" cannot mean a published artifact. Recommended reading: the "SDK" is the Java API in the `platform.authorization` package plus the HTTP administration API described by the committed OpenAPI file, from which the typed frontend client is already generated. "Stable" then means: changes to those two contracts are listed in the release upgrade notes. Publishing artifacts would need a new ADR that supersedes 0023 (OQ-88).

### AUTH-038 Custom authorization definitions

**Status:** Approved

Consuming applications define custom authorization resources, actions and attributes without modifying Kibootia's core. The definitions are subject to application-defined extension boundaries and to validation.

### AUTH-035 Service-to-service authorization

**Status:** Approved

The system supports authorization for service accounts and automated system identities. A service identity can receive narrowly scoped permissions, the system supports checks for machine-to-machine operations, and a service identity does not inherit the permissions of human users.

### AUTH-039 Identity-provider independence

**Status:** Approved

The authorization system is not tightly coupled to one identity provider. Keycloak is the supported identity provider; alternatives remain possible. Authentication and identity management stay conceptually separate from application authorization. The authorization system consumes trusted identity information from the supported providers.

### AUTH-040 Modular authorization capabilities

**Status:** Approved

Authorization capabilities can be activated modularly. An application enables or disables optional features according to its needs. A simple application is not made to configure multi-tenancy, nested groups, complex relationship models, multi-reviewer approval, delegation, advanced policy customization or any other enterprise capability it does not need.

The system gives a usable baseline and lets an application adopt more advanced capabilities when it needs them.

> **Review note:** Kibootia already has a tested way to make a capability optional: a property switch or a removable module, each with a recipe and a row in the CI matrix that proves the application still starts without it ([ADR 0024](../../../decisions/0024-package-modules-with-infrastructure-toggles.md)). Recommended: every optional authorization capability uses that mechanism, and "the baseline" is defined as what works with every switch off: role-based access with ownership, as today.
