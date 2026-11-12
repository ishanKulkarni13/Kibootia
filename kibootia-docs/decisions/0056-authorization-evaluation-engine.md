# 0056 Authorization evaluation engine

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Today the decision point is plain Java (`DefaultDecisionPoint` in the `access` module) reading roles from MySQL; no engine or expression library is in `pom.xml`.

## Context

[ADR 0053](0053-adopt-authorization-requirements.md) commits Kibootia to role-based, scope-based, relationship-based and attribute-based authorization. [ADR 0027](0027-central-authorization-decision-point.md) kept the evaluation in Java and a policy engine as a recipe; with the non-goal withdrawn, whether to build or adopt the evaluation is open again. The owner asked that mature tools be used where they fit, that nothing be reinvented without reason, and that no wrapper be written around a tool unless the wrapper adds something.

What the evaluation has to do, from the requirements and from [ADR 0055](0055-authorization-decision-contract.md):

1. Answer a single check with a reason.
2. Answer a list question as a **filter the database applies**, so paging and totals stay correct (AUTH-051, [ADR 0044](0044-authorization-enforcement-conventions.md)).
3. Apply a role change or a revocation on the next request (AUTH-033).
4. Keep administrator configuration unable to run arbitrary code (AUTH-002).
5. Run locally with no cloud dependency, under a licence a fork can use, on MySQL or without a store of its own.
6. Stay behind Kibootia's own port (AUTH-034).

**What Kibootia must own whichever tool is chosen.** An engine evaluates; it does not administer. Scoped assignments with validity and suspension, groups and their membership sources, delegation, separation of duties, the definition lifecycle, approval of sensitive changes, auditing of every change, and every administration screen are data and behavior no candidate below supplies. A candidate can only take over the evaluation of what Kibootia stores. This is also why Kibootia's port is not an empty wrapper: it carries the tenant, the 404 rule, the decision record and the list filter, none of which an engine's client provides.

## Decision

Owner decisions of 2026-11-11, recorded here; the first is confirmed or overturned by measurement before this ADR is accepted (OQ-84).

1. **Kibootia keeps its own store and its own evaluation of grants, scopes and relationships, in the `access` module, on MySQL.** Roles, assignments, scope nodes, group membership and relationship records are rows in Kibootia's database. A single check and a list filter are both answered from those rows.
2. **Conditions are named conditions written in Java** ([ADR 0054](0054-authorization-domain-model.md)). No expression language ships. CEL and Cedar are recorded below as the candidates if administrator-written conditions are ever needed.
3. **No external authorization service ships by default.** The port stays, so a fork can put an external engine behind it; the first such integration is written as a recipe when someone needs it.

**The measurement.** During milestone 7, outside the main code, two reference scenarios are built three ways: in-house on MySQL, with OpenFGA, and with Cerbos. The scenarios are "an HOD lists the tickets of their department, paged" and "only a requested reviewer may reject this ticket" ([reference scenarios](../product/requirements/authorization/reference-scenarios.md), T1 and T4). For each: the number of queries or calls and the latency of one check; the same for a paged list over 100,000 records; and what was needed to keep the 404 rule, the next-request revocation test and a refusal reason. If the in-house queries are clearly worse, the fallback is OpenFGA for relationship checks behind the same port, fed from Kibootia's tables through the outbox ([ADR 0031](0031-transactional-outbox-and-locked-jobs.md)), recorded in a new ADR.

## Alternatives considered

Checked on 2026-11-11 against the projects' own pages unless a cell says otherwise. "Not re-checked" means the statement is from general knowledge and must be verified before it is relied on. Nothing below was run or measured.

### Fit

| Candidate | Models it covers | List filtering in the database | Who holds the authorization data | Runs as |
|---|---|---|---|---|
| In-house on MySQL | Whatever is built: roles, scopes, relations, named conditions | Yes, by joins against Kibootia's own tables | Kibootia, one copy | Part of the application |
| OpenFGA | Relationships (Zanzibar style), roles expressed as relations, conditions in CEL | No. Its list query returns object IDs, by default at most 1,000 within 3 seconds, with no paging described | OpenFGA's store (PostgreSQL 14+ or MySQL 8), a second copy to keep in step | A separate server |
| SpiceDB | Relationships (Zanzibar style), conditions as "caveats" | No. Its own guidance is to use the lookup of accessible resources only for small results | SpiceDB's datastore, a second copy. Its MySQL datastore is documented as single primary with no read replicas (2024 package documentation; not re-checked for the current release) | A separate server |
| Cerbos | Roles and attribute conditions in YAML policies with CEL; no relationship graph | Yes. `PlanResources` returns a query plan the application turns into a filter | None: stateless. Roles and record attributes are sent with every call | A separate service or sidecar |
| Open Policy Agent | Anything expressible in Rego | Partly, through partial evaluation that the application must translate (not re-checked) | None by itself; data is pushed to it or sent per call | A separate service or sidecar |
| Cedar | Roles and attribute conditions; relationships through entity hierarchies | Partly. Partial evaluation exists in the Java binding, documented as a complement to full checks, not a replacement | None; entities are supplied per call | A library: Java binding over a Rust core through a native interface |
| CEL (`dev.cel`) | Conditions only; it is an expression language, not an authorization engine | No | Not applicable | A Java library |
| Casbin (jCasbin) | Roles with domains, simple attribute rules, from a model file (not re-checked) | Weak (not re-checked) | A policy table through an adapter | A Java library |
| Keycloak Authorization Services | Roles, scopes, policies inside the identity provider (not re-checked) | No | Keycloak | Part of the identity provider |
| Spring Security ACL | One access-control list per object (not re-checked) | By joins against its own tables | The application's database | Part of the application |

### Integration and operations

| Candidate | Java and Spring integration | Local development and deployment | Licence and governance | Maintenance signal |
|---|---|---|---|---|
| In-house on MySQL | Native; no new dependency | Nothing to add | Kibootia's own | Kibootia must maintain it |
| OpenFGA | Official Java SDK over HTTP or gRPC | One more container in Compose and in production, plus a synchronization step | Apache 2.0; CNCF Incubating since October 2025 | Active; originated at Auth0/Okta |
| SpiceDB | Client libraries (not re-checked for Java) | One more container plus a datastore | Apache 2.0; developed by AuthZed | Active (not re-checked) |
| Cerbos | Java SDK (`dev.cerbos:cerbos-sdk-java`) | One more container; policies as files or from Cerbos Hub | Apache 2.0 for the decision service; Cerbos Hub is a commercial product | Active; one vendor |
| Open Policy Agent | REST from Java; no first-party Java evaluator (not re-checked) | One more container or sidecar | Apache 2.0; CNCF graduated | Active. Its original maintainers moved from Styra to Apple in August 2025 (secondary sources); worth watching, not a reason by itself |
| Cedar | `com.cedarpolicy:cedar-java` (4.8.0 on Maven Central); the binding is documented as lagging the Rust core | A native library per platform inside the build and the image | Apache 2.0; originated at AWS | Active |
| CEL (`dev.cel`) | A plain Java library from Google | Nothing to add | Apache 2.0 | Active; version line still 0.x (0.14.0 seen) |
| Casbin (jCasbin) | A plain Java library (not re-checked) | Nothing to add | Apache 2.0 (not re-checked) | Not re-checked |
| Keycloak Authorization Services | Through Keycloak's adapters and APIs (not re-checked) | Already deployed | Apache 2.0 | Part of Keycloak |
| Spring Security ACL | Part of Spring Security | Nothing to add | Apache 2.0 | Maintained, with little new development (not re-checked) |

### Verdicts

| Candidate | Verdict | Deciding reason |
|---|---|---|
| In-house on MySQL | **Proposed default** | The only option that satisfies database-side list filtering and next-request revocation with one copy of the data. The cost is writing and testing hierarchy and relationship queries; the measurement exists to price that |
| OpenFGA | First recipe candidate; fallback for relationship checks | Bounded list query; a second copy of the data; an extra service for every fork |
| SpiceDB | Not selected | Same shape as OpenFGA with a weaker MySQL story |
| Cerbos | Second recipe candidate | Fits attribute rules and list filtering, but policies are files, which fits policy-as-code and not roles edited at runtime by administrators (AUTH-002) |
| Open Policy Agent | Recipe only, as ADR 0027 already decided | A general-purpose language is the opposite of requirement 4; input must be shipped per check |
| Cedar | Candidate for a condition language later | Good language for the purpose; a native library in every build |
| CEL | Leading candidate for a condition language later | Small, embeddable, not Turing-complete; not needed while conditions are named conditions |
| Casbin | Not selected | A second definition of the model in its own file; weak on relationships and lists |
| Keycloak Authorization Services | Not selected | Ties authorization to the identity provider, against AUTH-039 and [ADR 0025](0025-identity-and-authorization-data-split.md) |
| Spring Security ACL | Not selected | Per-object entries only; no scopes, conditions or relationship rules |
| A home-made expression or policy language | Rejected | The reinvention the owner asked to avoid |

## Consequences

- Kibootia writes and tests its own evaluation of scopes, groups and relationships. That is real work and a real risk: queries over hierarchies and nested groups must be correct and fast. The measurement prices the risk before it is accepted.
- One source of truth: the rows an administrator edits are the rows a decision reads, so the next-request revocation test of ADR 0044 keeps passing with no synchronization step.
- No new runtime dependency and no new service for forks. "Enterprise-grade without unnecessary complexity" is kept.
- What is given up: a relationship engine's ready-made model language and tooling, and the option of pointing at "we use OpenFGA" as assurance. The scenario tests and the security review have to carry that weight.
- "OPA as a recipe" in ADR 0027 becomes "an external engine as a recipe", with OpenFGA and Cerbos as the first candidates.
- If administrator-written conditions are needed later, the choice between CEL and Cedar is made then, in its own ADR, behind the existing "named condition" concept.

## Status

draft. The decisions in it are the owner's of 2026-11-11; the ADR is not accepted until the measurement has run and the owner has confirmed the result (OQ-84). On acceptance it partly supersedes [ADR 0027](0027-central-authorization-decision-point.md) (the "OPA as a recipe" part).

## Related

- [ADR 0053](0053-adopt-authorization-requirements.md), [ADR 0054](0054-authorization-domain-model.md), [ADR 0055](0055-authorization-decision-contract.md).
- [Technology catalogue](../technology/README.md).
- External pages consulted on 2026-11-11: the OpenFGA documentation at openfga.dev (relationship queries, conditions, project status) and the CNCF announcement of its incubation; the `cedar-java` and `cel-java` repositories and their Maven Central entries; the Cerbos documentation on filtering resources with query plans; the SpiceDB FAQ and MySQL datastore notes; secondary reports on Open Policy Agent's maintainers.
