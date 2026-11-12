# Authorization Review of 2026-11-11

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (a dated review). It describes commit `4cea34f`. It is a snapshot and is not kept in step with later changes; the living record is [traceability](../product/requirements/authorization/traceability.md).

A review of Kibootia's authorization code and documents against two requirements drafts in this folder, `authz-new-srs.md` (primary) and `authz-srs.md` (older). Its results are the permanent [authorization requirements](../product/requirements/authorization/README.md), four draft ADRs ([0053](../decisions/0053-adopt-authorization-requirements.md) to [0056](../decisions/0056-authorization-evaluation-engine.md)) and the [authorization roadmap](../product/roadmap/authorization-roadmap.md). This document holds the reasoning that has no other home.

**How it was done.** The backend, the frontend, the infrastructure, the CI workflow, all 52 ADRs and the permanent documents were read. CI status was checked with the GitHub CLI. No build and no test was run for this review. The Timetia and indent-edi repositories are not part of this repository and were not inspected; what is said about them comes from the drafts.

## 1. Verdict

1. **What exists is small and well built.** Role-based access with code-defined permissions, runtime-editable roles, ownership, a tenant check, one decision point behind a port, and an architecture test that fails the build when a use case has no check. It is verified in CI, documented, and backed by ADRs with rejected alternatives recorded. An experienced reviewer would find little to object to in what is there.
2. **It covers a small part of the new requirements.** Of 51 requirements, 4 are fully met, 21 partly, 25 not at all, 1 is deferred by the requirement itself.
3. **The gap is mostly in three shapes, not in missing features.** The decision returns a boolean; scope is an enum of three values encoded in permission names; an assignment is only "user holds role". Almost every unmet requirement runs into one of the three. Changing those shapes once, first, is the core of the plan.
4. **The requirements are a good draft with real defects**: two requirements referenced but never defined, no measurable number anywhere, thirty open questions, and a few places where they would pull a workflow engine into the foundation.
5. **The commitment is large.** Nine slices against six milestones built so far, for two applications whose permission rules are not confirmed. The owner chose full commitment knowingly; the roadmap is ordered so the slices with a consumer come first.

## 2. Terms used in this review

| Term | Plain meaning |
|---|---|
| Role-based access (RBAC) | You may do something because of a role you hold. "Editors may edit notes" |
| Scope | Where a role applies. "Administrator of the Physics department", not of everything |
| Relationship-based access (ReBAC) | You may do something because of how you are connected to the record. "The assigned reviewer of this ticket may approve it" |
| Attribute-based access (ABAC) | You may do something because of facts about you, the record or the moment. "Only while the indent is a draft" |
| Decision point | The one component every check goes through. It answers "may this caller do this to this record" |
| Port | An interface the application owns, so the implementation behind it can be replaced |
| Deny-overrides | When one rule allows and another forbids, the forbidding rule wins |
| Fail closed | When something goes wrong while deciding, the answer is no |
| Separation of duties | One person must not hold or use two powers that should check each other, such as requesting and approving |
| Zanzibar-style engine | A service, modelled on Google's internal system, that stores "who is related to what" and answers questions about it. OpenFGA and SpiceDB are examples |

## 3. Current state

### Implemented and verified in CI

- Token validation as an OpenID Connect resource server: signature, issuer, expiry, audience; service-account tokens refused (`platform/security`, `ResourceServerIT`).
- A request gate that creates the profile on first use and refuses disabled or deleted users from the next request (`ProfileGate`, `IdentityGateIT`).
- Permissions declared in code per module, validated at startup; roles stored in the database and edited at runtime; two built-in roles (`access` module, `AccessApiIT`).
- One decision point (`PolicyDecisionPoint` in `platform.authorization`, `DefaultDecisionPoint` in `access`) with `can` for one record and `scopeFor` for lists.
- Ownership and "any" variants; an invisible record answers 404 (`NotesApiIT`).
- Safeguards: an editor grants only what they hold; only an administrator grants the administrator role; the last administrator cannot be removed, tested concurrently.
- No permission cache; a role change applies on the next request on a second instance (`TwoInstancesIT`).
- Tenant column on every table with a fail-closed filter; cross-tenant tests.
- An append-only audit log of administrative changes, written in the transaction of the change, people referred to by ID only.
- Administration screens for users, roles, assignments and the audit log, shown or hidden by permission.
- Milestone 5: backend, frontend, image scan and all four module-removal rows passed in CI (pull request #7, run 42959703043; `main`, run 42981706613).

### Partly implemented

- List filtering: three fixed values, used by one module. The user list is all-or-403.
- Tenant isolation: schema and filters are complete; at run time there is one tenant, `TenantResolver` has no implementation, and jobs name the default tenant in code.
- Identity-provider independence: administration calls sit behind a port, but the token subject must be a UUID and the service-account check follows Keycloak's naming.
- Audit: changes are recorded; decisions are not. A denial leaves no trace beyond the 403.
- Milestone 5 end-to-end tests: the job was **skipped** in both CI runs, so the 31 browser tests have not run in CI (OQ-86).

### Planned in accepted documents, not built

- An OPA recipe, a server-side session store recipe and other recipes (milestone 6).
- A permission cache checked against a version number, "if measurement shows a need" (ADR 0044).
- Service principals "as a future recipe" (ADR 0044).
- "Later resource grants and tenant memberships" (ADR 0025).
- Milestone 6 as a whole.

### Extension points with nothing behind them

- `ResourcePolicy`: no implementation outside a unit test.
- `TenantResolver`: only the single-tenant default.
- `Permission.beforeVerification()`: no module uses it.

### In the code but not stated in the documents

- `@RequiresPermission(X)` passes when the caller holds X for own records only, because `Authorization.can(action)` is "scope is not none". Correct for the uses that exist, but a trap for a new use case; [authorization](../architecture/identity/authorization.md) does not mention it.
- The system caller holds every permission and the reason passed to `runAsSystem` is logged at DEBUG only. The architecture documents mention that jobs run "as the system" and not what that authority covers.
- The frontend guards the administration area as a whole (`AdminFrame`), not each section.
- There is no `README.md` at the repository root, and `backend/HELP.md` is the generator's default file and still lists a removed dependency.

## 4. Decisions already made

| Decision | Backed by | Kind |
|---|---|---|
| Keycloak owns identity; Kibootia's database owns roles and assignments; tokens carry identity only | ADR 0025 | Accepted, built |
| One decision point behind a port; policies in Java; OPA as a recipe only | ADR 0027 | Accepted, built |
| Checks on use-case methods; permission names; three-value list scope; no cache; machine clients refused | ADR 0044 | Accepted, built |
| Contract in `platform`, implementation in `access`; ownership by the `-any` naming convention; 404 versus 403 | ADR 0049, ADR 0043 | Accepted, built |
| Role semantics, the request gate, two lookups per request | ADR 0050 | Accepted, built |
| Tenant-ready schema, single tenant, no tenant management | ADR 0028 | Accepted, built |
| Audit log design | ADR 0046 | Accepted, built |
| Role-based first; a full attribute engine is not an initial requirement | ADR 0009 | Accepted; superseded by draft 0053 |
| Applications copy the repository; nothing is published as a library | ADR 0023 | Accepted; no release exists yet |

**Assumptions that are not decisions:** that Timetia and indent-edi will be built on Kibootia (no permanent document says so; the only earlier mention of Timetia is an example folder name); every indent-edi rule in the drafts; every performance expectation.

## 5. What to keep

These are the decisions a larger system most often gets wrong, and they are already right here.

- **One decision point, behind an interface the application owns.** Everything in the new requirements can grow behind it.
- **Checks on use cases, not controllers, enforced by a build-breaking test.** Other modules, jobs and event handlers call use cases too.
- **Lists filtered by the database.** The alternative, loading rows and filtering in memory, breaks paging and does not scale. This rule also turns out to decide the engine question (section 8).
- **No cache until measured.** A revocation that applies on the next request is exactly what AUTH-033 asks for, and it is proven by a two-instance test.
- **Fail closed everywhere:** no tenant, no rows; no decision point, deny all; undeclared permission, startup failure.
- **Anti-escalation in the role editor and the last-administrator lock.**
- **Audit entries in the transaction of the change, with no personal data.**
- **The discipline around it:** ADRs with rejected alternatives, "verified" meaning a named CI run, architecture rules shown to fire on deliberately broken classes.

## 6. Weaknesses and technical debt

**In the authorization design** (each blocks requirements; see [ADR 0055](../decisions/0055-authorization-decision-contract.md) for the proposed change)

| Finding | Where | Effect |
|---|---|---|
| A decision is a boolean | `PolicyDecisionPoint` | No reason; nothing to audit or explain |
| Scope lives in permission names (`-any`) | `DefaultDecisionPoint`, `PermissionRegistry` | Every new scope type would multiply the permission list |
| The resource is an untyped object | `can(Caller, String, Object)` | The decision point cannot ask for scope, relations or attributes |
| The caller has no type | `Caller` | A service cannot be a caller |
| A module rule replaces the convention and is first-match | `ResourcePolicy` | A rule could widen access past the roles; no combination, no deny |
| The system caller is all-powerful and quiet | `Authorization.runAsSystem` | Not least privilege; not visible above DEBUG |
| The administrator role holds everything implicitly | `StoredPermissionSource` | Conflicts with separation of duties and with scope-limited administrators later |
| A method-level check passes on own-records reach | `Authorization.can(String)` | Easy to misuse on a new instance-level action |
| The coverage rule misses checks inside lambdas and accepts any call as a check | `AuthorizationRules` | Documented blind spot; still a blind spot |

### In the frontend

- No per-section guard in the administration area; a user with one administrative permission can open another section's page and gets the API's 403 as an error box.
- An expired session answers `bff.unauthenticated`, which has no message; screens show "The request failed" and do not redirect to sign-in.
- Permissions are read once when the page shell renders. Inside the administration layout they may stay stale until a full reload. This is a reading of the framework's behavior, not something that was run.
- A redirect to sign-in from the page shell drops the page the user wanted.

### In the documents

- ADR 0043 says a record the caller may not view answers 404; ADR 0051 says another user's profile or avatar answers 403. Neither records the exception (OQ-87).
- ADR 0025 still describes a permission cache; ADR 0044 and ADR 0050 decided against one. The index says only "extended by".
- Milestone 1's file says "not complete, CI never run"; implementation status says its jobs passed.
- Many documents still say "planned" or "nothing exists" about things that are built: the architecture overview and its indexes, `authentication.md`, `token-validation.md`, `package-structure.md`, the testing strategy, `container-deployment.md`, `secrets-management.md`, the vision, several rows of implementation status, and the Implementation lines of ADRs 0006, 0007, 0008, 0011, 0012, 0018, 0021, 0028, 0031, 0034, 0037, 0038 and 0043.
- The documentation root sends readers to a readiness review in this folder that predates milestone 0, although this folder is defined as material nothing may rely on. This folder also has no `README.md`.
- Two broken section links: in `engineering/README.md` and in `recipes/README.md`.
- The container image scan named in OQ-44 has now passed in CI; the entry was not updated.

Only the milestone 5 status was corrected in this round. The rest is listed so it can be cleared in one documentation pass.

## 7. The requirements drafts reviewed

**The two drafts compared.** The older draft is a compact list of AUTH-001 to AUTH-040 with a responsibility table and a three-way scope split. The newer one rewrites it at five times the length, adds AUTH-041 to AUTH-049, reference scenarios and thirty open questions, and drops two things: the definitions of AUTH-032 and AUTH-033, and the responsibility table. The older draft calls its content "agreed during discovery"; the newer calls itself a draft for approval. The permanent set takes the newer text, restores what was dropped, and keeps the newer status.

### Defects fixed in the permanent set

| Defect | Fix |
|---|---|
| AUTH-032 and AUTH-033 listed for planning but never defined | Restored from the older draft |
| Two requirement sections without an ID | AUTH-050 and AUTH-051 |
| Seven status labels in use against four defined | Mapped to the four |
| AUTH-045 as a chapter of its own, after the summary table that lists it | Placed with time-bound authorization |
| A 2,000-line single file | Split by topic under the size rule |

**Problems that need an owner decision** (each is a review note in the requirement and, where it blocks work, an open question)

1. **Nothing is measurable.** "Efficient enough", "normal request volumes", "suitable audit records". Without numbers the non-functional section cannot be tested, and an engine cannot be chosen on performance (OQ-70 to OQ-73).
2. **AUTH-013 mixes authorization with counting.** "Is this person an eligible reviewer" is an authorization question. "Have all reviewers approved" and "is the quorum reached" are counts over workflow history. Answering the second kind inside the decision point makes it a workflow engine, which the drafts themselves rule out.
3. **AUTH-030 does not separate definitions from operational data.** Draft, publish and rollback suit roles and policies. Applied to assignments they would delay every new staff member and could resurrect a revoked assignment on rollback.
4. **AUTH-022 cannot share the existing audit table.** Decisions outnumber changes by orders of magnitude, and that table is written inside business transactions.
5. **AUTH-032 and AUTH-033 conflict** unless one is given priority. Recommended: revocation wins; a cache must be unable to serve a revoked permission.
6. **AUTH-037 "SDKs" contradicts ADR 0023**, under which nothing is published. Recommended reading: the in-repository Java API and the OpenAPI-described administration API (OQ-88).
7. **AUTH-020 "explicit deny wins"** presupposes deny rules, which do not exist and which make every explanation harder. Recommended: an optional capability.
8. **AUTH-006, 011, 015 and 043 are one mechanism** described four times. Designing them separately would produce four attribute systems.
9. **AUTH-025 bundles** add an indirection between role and permission. Worth it only when several roles share a large set.
10. **AUTH-017: tenant and department are different things.** A tenant isolates data. A department limits permissions inside one tenant. The drafts list both under "organizational isolation".
11. **"Without modifying Kibootia's core"** needs a meaning in a model where the application owns a copy of all the code. Recommended: without editing the foundation's modules, so that merging a new release does not conflict.
12. **The reference validation is hypothetical.** indent-edi has no permission matrix, and section 24 of the newer draft says so. Multi-tenancy is "optional" for both applications yet is a whole slice.

### Conflicts with accepted decisions

| Requirement | Accepted decision | Resolution |
|---|---|---|
| AUTH-003, 005, 006 | ADR 0009: attribute engine not an initial requirement | Draft ADR 0053 supersedes 0009 |
| AUTH-018, 035 | ADR 0044: machine clients refused | Replaced in milestone 7 |
| AUTH-017 | ADR 0028: no live tenancy, no tenant management | Replaced in the tenancy slice |
| AUTH-032 | ADR 0044, ADR 0050: no cache | No conflict if the cache stays conditional on measurement |
| AUTH-037 | ADR 0023: no published libraries | Open (OQ-88) |
| Any shipped engine | Goals and non-goals; ADR 0027 | Non-goal withdrawn by draft 0053; engine decided by draft 0056 |

## 8. Tools

The full comparison is in draft [ADR 0056](../decisions/0056-authorization-evaluation-engine.md). The reasoning in short:

- **An engine evaluates; it does not administer.** Scoped assignments, validity periods, groups, delegation, approval, draft and publish, and every screen are Kibootia's to build whichever engine is chosen. The realistic saving from an engine is the evaluation of relationship graphs and conditions, not the bulk of the work.
- **The list rule decides most of it.** Kibootia filters lists in the database. OpenFGA's list query is bounded (1,000 results and 3 seconds by default) and SpiceDB's own guidance is to use its equivalent for small results. Both would force "fetch permitted IDs, then query", which is the pattern ADR 0044 rejected. Cerbos returns a query plan, which fits, but it is a separate service with policies in files.
- **Two copies of the truth.** A relationship engine keeps its own store. Every assignment an administrator makes would have to reach it before the next request, or AUTH-033 is broken.
- **Recommended:** keep the store and the evaluation of roles, scopes and relationships in Kibootia on MySQL; adopt an embedded, non-executable expression language (CEL, with Cedar as the alternative) for administrator-written conditions; keep the port so a fork can attach OpenFGA or Cerbos; confirm all of this with a measured evaluation before accepting the ADR.
- **What not to build:** a home-made expression language, a home-made policy language, or a general workflow engine for approvals.

## 9. Risks

| Risk | Why it matters | Mitigation in the plan |
|---|---|---|
| Building for consumers that do not exist yet | Generic features designed without a real user tend to be wrong in detail | Scenario tests per slice; slices without a consumer last |
| The foundation absorbing business logic | Reviewer counting, deadlines and approvals are one step from a workflow engine | Review notes on AUTH-013, 029, 045; the responsibility table |
| The contract changing after forks exist | Every fork merges the break | Milestone 7 before the first release tag (OQ-85) |
| The permission migration changing access silently | The one step where a bug gives or removes access for real users | Before-and-after equivalence test in milestone 7 |
| In-house relationship queries being slow or wrong | Recursive SQL over groups and hierarchies | Measured evaluation in ADR 0056; OpenFGA as the recorded fallback |
| Complexity for small applications | Every fork inherits every capability | A switch and a CI row per capability; the baseline is today's behavior |
| Scope outrunning verification | Milestone 5's browser tests have still not run in CI | OQ-86; "verified" keeps meaning a named CI run |
| One maintainer | Nine slices, each the size of the largest milestone so far | Stated in ADR 0053 and the roadmap; the order allows stopping after any slice with a working system |

## 10. What the owner needs to decide

In order of what blocks what. All are in [open questions](../decisions/open-questions.md).

1. Confirm draft ADR 0053, the scope decision in writing (OQ-81).
2. Milestone 7 before or after milestone 6 (OQ-85).
3. Confirm drafts 0054 and 0055, the model and the contract (OQ-82, OQ-83). Milestone 7 cannot start without them.
4. Approve or reject the nine proposed requirements, AUTH-041 to AUTH-049 (OQ-80).
5. Run the engine evaluation and decide draft 0056 (OQ-84). Needed before slice 8.
6. Set the first numbers (OQ-70 to OQ-73).
7. Run the end-to-end tests in CI for milestone 5 (OQ-86).
8. The thirty questions carried over from the drafts (OQ-50 to OQ-79), each needed only by the slice named beside it.
