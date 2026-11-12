# Authorization Security and Engineering Review of 2026-11-11

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (a dated review). It describes commit `4cea34f`. Every finding comes from **reading** the code and documents; nothing was run, no test was written and no exploit was tried. A finding marked **inferred** is a reading that needs a test before it is trusted.

Companion to the [authorization review](authorization-review.md). It covers the security checklist and the engineering-quality checklist of the review brief. "Addressed in" names the phase of the [authorization roadmap](../product/roadmap/authorization-roadmap.md).

## How to read it

Each row says what the code does today, the risk, and where the plan addresses it. Severity is the reviewer's judgement for a system that is not yet in production: **High** (a user can gain access they should not have, today), **Medium** (a gap that becomes a hole when a planned capability arrives, or a weakness that needs a test), **Low** (hygiene).

Plain meaning of two terms used below: a **time-of-check to time-of-use race** is when the system checks that something is allowed and then, a moment later, acts on a world that has changed; **fail open** means that when something goes wrong the system lets the request through instead of refusing it.

## 1. Security findings

### S1 Role assignment lets a user take roles they could not create (High, read in code)

- **Today:** `RoleAssignmentService.replaceRolesOfUser` (`access/application`) requires `access.roles.assign`, then adds and removes the requested roles. The only role it protects is the administrator role (`requireAdministrator`). It does not check that the caller holds the permissions of the roles being added. `assignInitialRoles`, used when inviting a user, has the same shape.
- **Contrast:** the role editor refuses to give a role a permission the editor does not hold (`RoleService.requireGrantable`).
- **Risk:** a user who holds `access.roles.assign` and nothing else can give themselves, or anyone, every existing role except administrator. If a role holds `identity.users.delete` or `audit.entries.purge`, that user can acquire it. Whether this is a real hole depends on who is given `access.roles.assign`; it is a permission whose name suggests a narrow power and whose effect is wide.
- **Owner decision (2026-11-11):** fix it. A user may assign only roles whose permissions they hold.
- **Addressed in:** milestone 7, first item, with allowed and denied tests.

### S2 Permissions are read once per transaction, not once per decision (Medium, inferred)

- **Today:** each decision reads the caller's permissions with one query (`StoredPermissionSource`). Use cases are transactional, and MySQL's default isolation level (repeatable read) shows a transaction the data as of its first read. `replaceRolesOfUser` sets `READ_COMMITTED` explicitly for its last-administrator count, which suggests the default is repeatable read in the rest of the code.
- **Risk:** a revocation committed while a long use case is running does not reach that use case until it finishes. For a short request this is the "next request" guarantee of AUTH-033 and is acceptable. For a bulk operation (AUTH-048) of thousands of records it is not.
- **Addressed in:** milestone 11 (bulk operations authorize at execution, and the maximum staleness inside one transaction is stated); a test shows the behavior either way.

### S3 A module rule can widen access, and the first match decides (Medium, read in code)

- **Today:** in `DefaultDecisionPoint.can`, the first `ResourcePolicy` that supports a record returns the final answer. It is consulted only after the caller holds the permission, but it may allow a record the ownership convention would refuse, and a second policy is never consulted.
- **Risk:** no production module implements it, so nothing is exposed now. The first module that does can silently widen what roles say, which defeats the role editor and the audit trail as descriptions of who can do what.
- **Addressed in:** milestone 7 (a rule can only narrow; deny overrides).

### S4 No explicit deny exists, so "deny wins" cannot be used (Medium, read in code)

- **Today:** every rule is an allow; absence of a rule is a refusal.
- **Risk:** low now. It matters when groups and inheritance arrive: a user in a group inherits permissions the administrator may want to remove for one person, and with allow-only rules the only tool is to restructure roles.
- **Addressed in:** milestone 7 introduces the combination rule; deny grants are an optional capability from milestone 8.

### S5 System authority is total and quiet (Medium, read in code)

- **Today:** `Authorization.runAsSystem` gives every permission within the tenant. `DurableEventInterceptor` wraps every durable event handler in it, and `RetentionPurgeJob` and `ReconciliationJob` use it. The reason is logged at DEBUG only and is not audited.
- **Risk:** a bug or a malicious event handler in any module can do anything, with no record above debug level. A new event handler inherits total authority by default.
- **Addressed in:** milestone 7 records the reason in the decision log; milestone 12 replaces the all-powerful caller by named service principals with declared grants.

### S6 The machine-token check is a Keycloak heuristic and can fail open (Medium, inferred)

- **Today:** `UserTokenValidator` rejects a token that has a `client_id` claim or whose `preferred_username` starts with `service-account-`. The caller is a user if `sub` parses as a UUID (`Authorization.caller`).
- **Risk:** a machine token from another provider, with a UUID subject and none of those claims, would pass as a human user, and with a profile created by the request gate would receive the default `user` role. Conversely a human whose username begins with `service-account-` would be refused. Keycloak is the only provider today, so neither happens now; the risk is the day a second provider or a different client configuration appears.
- **Addressed in:** milestone 12 (one identity-owned token-to-principal mapping; machine principals registered explicitly, never inferred from claim shape).

### S7 Cross-tenant isolation has no automated guard for new native queries (Medium, read in code and documents)

- **Today:** tenant isolation is a Hibernate `@TenantId` filter plus explicit `tenant_id` in every hand-written SQL statement (`JpaRoleStore`, `StoredPermissionSource` and others). [ADR 0028](../decisions/0028-tenant-ready-schema.md) says native queries bypass the filter and "need explicit review and tests". Of the architecture rules (1, 4 to 11), none checks that a native query names the tenant.
- **Risk:** one forgotten `tenant_id` in a later query leaks across tenants. With a single tenant nothing can leak today, which is why nothing would catch it; it becomes real in the multi-tenancy phase.
- **Addressed in:** milestone 8 adds an architecture rule or a startup check for SQL strings without a tenant parameter; milestone 16 adds isolation tests with two live tenants.

### S8 Invisible records: 404 and 403 are used inconsistently (Medium, read in documents)

- **Today:** a note the caller may not see answers 404 so existence is not revealed ([ADR 0043](../decisions/0043-http-api-conventions.md)); another user's profile or avatar without `identity.profiles.view` answers 403 ([ADR 0051](../decisions/0051-milestone-4-implementation-decisions.md)). Whether `GET /users/{id}` answers 404 for a user that does not exist, before the permission check, was not checked.
- **Risk:** where the answer differs between "does not exist" and "exists but forbidden", a caller can probe which IDs exist (an insecure direct object reference of the information kind). IDs are random UUIDs, which makes guessing impractical but does not remove the principle.
- **Addressed in:** milestone 7 records one rule (OQ-87) and a test per resource that existing and non-existing IDs answer identically to a caller without access.

### S9 Delegation, groups and administrative boundaries are not built (design risks to carry)

| Risk | Where it would arise | What the design must do | Addressed in |
|---|---|---|---|
| **Delegation abuse:** a delegate gets more than the delegator has, keeps authority after the delegator loses it, or re-delegates | Delegation (AUTH-012) | Evaluate every delegated decision against the delegator's authority **at that moment**; forbid delegating the power to assign roles or to delegate; no re-delegation unless an application enables it; record both identities in every audit entry and decision record | Milestone 14 |
| **Group cycles and concurrent cycles:** two administrators add group A to B and B to A at the same moment; each check passes before the other commits | Nested groups (AUTH-018) | Refuse cycles when membership is written, serialized by a lock (the same technique `ensureNotLastAdministrator` uses on the administrator role), plus a depth limit and a test with concurrent writers | Milestone 13 |
| **Administrator boundary:** the built-in administrator holds every declared permission implicitly, including permissions a module adds later | Scoped and tenant administrators (AUTH-028) | Make the administrator an ordinary role with explicit grants at the tenant root, so a scoped administrator is a role with fewer grants, not a special case | Milestones 8 and 15 |
| **Privilege escalation through configuration:** a role editor attaches a condition or a scope that widens what a role reaches | Named conditions, scoped assignments | The "only what you hold" rule applies to reach and conditions as well as actions; validation before activation | Milestones 9, 11, 15 |
| **Stale or invalid configuration made active** | Configuration lifecycle (AUTH-030) | Validation refuses a definition that references an undeclared action, scope type or condition; roll back restores definitions only, never assignments | Milestone 15 |

### S10 Audit data (Low to Medium, read in code and documents)

- **Today:** `AuditEntry` rejects personal-data field names and any value containing `@`, and records people by ID only ([ADR 0046](../decisions/0046-audit-log-design.md)). The audit viewer needs one log-wide permission, `audit.entries.view`, and viewing is not itself recorded.
- **Risk:** the log-wide permission cannot express "the author of a ticket may read that ticket's history" (Timetia needs exactly that), and access to a sensitive log leaves no trace. Decision records, once added, can leak resource attributes if they record values.
- **Addressed in:** milestone 10 (per-record history visibility); milestone 15 (the decision log stores principal, action, resource reference, outcome, reason code and trace ID, and no attribute values; reading the log is recorded).

### S11 Fail-open and fail-closed paths (read in code)

| Path | Behavior | Verdict |
|---|---|---|
| No decision point bean | `Authorization` falls back to deny-all | Closed |
| Undeclared action name | `IllegalStateException`, mapped to a server error | Closed |
| No tenant bound | Queries find nothing and writes are refused | Closed |
| Token without a UUID subject | `AccessDeniedException` | Closed |
| `RequestGateFilter` registered only if a `RequestGate` bean exists (`SecurityConfiguration`) | If the bean were missing, the disabled-user check would silently not run | **Fails open on a wiring mistake**; identity is core and tested, so low likelihood (Medium, inferred) |
| An error while evaluating a future rule or condition | Not defined, no such rule exists | Must deny and log as an error; test by injecting each failure (milestone 7) |

### S12 Stale decisions and caching

- **Today:** no cache anywhere in the authorization path. The Keycloak admin token is cached in memory by the adapter, which is outside authorization.
- **Risk:** none now. A future cache (AUTH-032) is the single largest source of stale-permission bugs in systems of this kind.
- **Rule for later:** a cache is allowed only in a form that cannot serve a revoked permission, for example validated against a version number on every use, and only after a measured target is missed (milestone 17). Until then the next-request guarantee stays as the verified behavior (`TwoInstancesIT`).

### S13 Frontend authorization gaps (Low, read in code)

- The administration area is guarded as a whole (`AdminFrame` shows content if the user holds any one of three permissions); a user with only `audit.entries.view` can open the Users screen and sees the API's 403 as an error box.
- An expired session yields the BFF answer `bff.unauthenticated`, which no screen maps, so the user sees "The request failed" and is not sent to sign in.
- Permissions are loaded when the page shell renders; inside the administration layout they may stay stale until a reload (inferred from framework behavior, not run).
- None of these is a security hole, because the backend enforces every check; they are usability defects in how denial is presented. All three are scheduled in milestone 7.

## 2. Engineering-quality findings

### Q1 Loose coupling

| Area | State | Finding | Action |
|---|---|---|---|
| Business domain | Clean | No Timetia or indent-edi term appears in the foundation | Keep; scenarios live in test fixtures |
| Other modules | Good | The decision point is a port in `platform`; modules use the `Authorization` facade; Modulith and ArchUnit verify the edges | Keep |
| Identity provider | Partial | Administration sits behind `IdentityProviderAdmin`; but the token subject must be a UUID, claim names and the service-account test follow Keycloak, and the frontend builds Keycloak endpoint paths by hand instead of discovery (`oidc.ts`) | One token-to-principal component; frontend discovery is optional (milestone 12) |
| Database | MySQL-specific by design | Generated columns for soft-delete uniqueness (`active_marker`), `BINARY(16)` identifiers, MySQL duplicate-key mapping | Acceptable (ADR 0011); hierarchy and group queries will use MySQL 8 recursive queries, which should stay in one module |
| Policy engine | Open | The port is the seam; nothing engine-specific exists | Decided by measurement ([ADR 0056](../decisions/0056-authorization-evaluation-engine.md)) |
| Workflow | Clean | Statuses are supplied by the application; no workflow concept in `access` | Keep; attributes are the only channel |

No abstraction is proposed that the requirements do not need. The one new seam, a typed resource reference supplied by the module that owns the record, exists because the decision point must not query another module's tables ([ADR 0055](../decisions/0055-authorization-decision-contract.md)).

### Q2 Quality of the engineering

| Area | Finding |
|---|---|
| Module boundaries and dependency direction | Strong: Modulith verification, rule 8 (`platform` depends on nothing), rule 5 (nothing depends on a removable module), each shown to fire on a deliberately broken fixture |
| Separation of models | Strong: DTOs, application models and entities are separate (ADR 0004); hand-written mappers |
| Public API versus internals | Strong: sub-packages are internal by convention and checked |
| Security enforcement | Strong on coverage (rule 7), with two documented blind spots: a check inside a lambda or helper is not seen, and any direct call counts as a check |
| Error handling | Strong: Problem Details with stable codes; the contract version 2 adds stable reason codes |
| Observability | Weak for authorization: denials are neither logged nor counted; log export is not wired (OQ-42). Milestone 7 adds structured denial records; metrics on denials by reason follow in milestone 15 |
| Testing | Strong: allowed and denied tests per operation, a two-instance freshness test, concurrency test for the last administrator, tenant isolation tests. Gap: no property or fuzz tests for the decision point, no test for the equivalence of a data migration (added in milestone 8) |
| Migration strategy | Flyway per module with `ddl-auto=validate`; a role-permission migration is needed in milestone 8 and carries an equivalence test |
| Backward compatibility | Not required: no forks and no users yet (owner, 2026-11-11). Breaking changes are acceptable and are made without compatibility shims |
| Documentation | Good discipline (status vocabulary, ADRs with alternatives), stale text in many documents ([review](authorization-review.md#6-weaknesses-and-technical-debt)) |

### Q3 What could make an experienced engineer doubt the quality

1. The assignment gap in S1: a security rule applied in one place and not its sibling.
2. A decision that answers only yes or no, with no reason, in a system that is meant to be auditable.
3. Permission names that carry meaning as a suffix (`-any`), which a reviewer will read as a convention standing in for a missing concept.
4. A system caller that can do anything and is invisible in logs.
5. Documents that say "planned" about built things; a reader cannot tell what exists.

All five are in the plan.

## 3. Summary

| # | Finding | Severity | Phase |
|---|---|---|---|
| S1 | Role assignment does not require holding the roles' permissions | High | 7 |
| S2 | Permissions snapshot per transaction (bulk operations) | Medium | 11 |
| S3 | A module rule can widen access; first match decides | Medium | 7 |
| S4 | No explicit deny | Medium | 7, 8 |
| S5 | System authority total and quiet | Medium | 7, 12 |
| S6 | Machine-token heuristic is Keycloak-specific and can fail open | Medium | 12 |
| S7 | No automated guard for tenant-less native SQL | Medium | 8, 16 |
| S8 | Inconsistent 404 and 403 for invisible records | Medium | 7 |
| S9 | Delegation, group-cycle and administrator-boundary risks to design against | Design | 13, 14, 15 |
| S10 | Log-wide audit permission; reading the log is not recorded | Low to Medium | 10, 15 |
| S11 | Registration of the request gate depends on a bean | Medium (inferred) | 7 |
| S12 | Any future cache must not serve revoked permissions | Rule | 17 |
| S13 | Frontend guard and session-expiry defects | Low | 7 |

Nothing here says the system is secure. It says that the checks that exist are well placed and tested, that one clear gap exists (S1), and that most of the planned work removes a risk before it can appear.
