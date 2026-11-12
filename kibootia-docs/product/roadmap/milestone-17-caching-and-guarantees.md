# Milestone 17: Measured Targets, and Caching Only If Needed

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists. There is no authorization cache by decision ([ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md), [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md)); a role change applies on the next request and a second-instance test proves it.

A phase of the [authorization roadmap](authorization-roadmap.md), and the only conditional one. It may end with the decision **not** to build a cache.

## 1. Objective

Turn the intentions of the non-functional requirements into numbers, measure the system against them, and add a decision cache only if a number is missed and only in a form that cannot serve a revoked permission.

## 2. Requirements covered

AUTH-032, AUTH-033, and the performance, scalability and reliability rows of the [non-functional requirements](../requirements/authorization/non-functional.md). Open questions OQ-68 to OQ-73.

## 3. Existing implementation affected

None if the measurement meets the targets. If a cache is built: `access/persistence/StoredPermissionSource` and the equivalent sources for assignments, groups, relationships and delegations, and the places that change them.

## 4. New functionality delivered

- Targets, set by the owner from the baselines recorded in milestones 7 and 9 and from expected sizes (OQ-70 to OQ-72): a latency target for one decision and for a paged list, at stated numbers of users, groups, relationships and records.
- A repeatable load test that measures decisions and lists at those sizes, run in CI nightly, not on every pull request.
- A written result: targets met, or the slowest queries and their causes.
- **Only if a target is missed:** a cache with a per-tenant authorization version number. Every change that can alter a decision increments the version in the same transaction; a cached entry is used only if its version equals the current one, which is read on every request. Revocation therefore still applies on the next request, on any number of instances.
- A switch for the cache, off by default.

## 5. Modules affected

None if no cache is built. Otherwise `access` and every module whose writes change authorization data.

## 6. Dependencies

The phases whose cost is being measured: at least milestones 9 and 10, ideally all. The targets need the owner (OQ-70 to OQ-73).

## 7. Database changes and migration strategy

None unless a cache is built, in which case one additive table of version counters per tenant.

## 8. Security implications

- A cache is the largest source of stale-permission bugs in authorization systems. The only allowed design validates against a version number on every use.
- Every code path that changes authorization data must increment the version. A test enumerates the write paths, and an architecture rule or a test fails when a new write path does not.
- A cache hit must never survive a tenant switch; keys include the tenant.
- If a cache is built, the maximum staleness is zero by design, and a test proves it across two instances for each kind of change.

## 9. Automated tests required

- The load test with its thresholds, run nightly.
- If a cache is built: a cached decision is never served after a role, assignment, scope, group, relationship, delegation or definition change, one test per kind; two instances; concurrent change and read; a missed increment is caught by the write-path enumeration.
- With the switch off, every previous test passes.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), the [non-functional requirements](../requirements/authorization/non-functional.md) (the numbers), the [observability baseline](../../operations/monitoring/observability-baseline.md), [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), and an ADR recording the targets and the decision to build or not to build a cache.

## 11. Acceptance criteria

1. Targets are written, with the owner's agreement.
2. The load test runs nightly and its result is recorded.
3. Either every target is met with no cache, and the ADR says so; or a cache exists, with the zero-staleness tests passing.
4. All of the above pass in CI.

## 12. Rollback and recovery

A cache is switched off by its property, which returns to reading the database on every decision. Nothing else depends on it.

## 13. Usable state at the end of the phase

Unchanged for users. The system has stated performance targets that are checked every night.
