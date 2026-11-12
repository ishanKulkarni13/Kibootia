# Milestone 13: Groups

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing of this milestone exists; there is no group concept in the code.

A phase of the [authorization roadmap](authorization-roadmap.md). Decision: draft [ADR 0054](../../decisions/0054-authorization-domain-model.md); an ADR on groups and membership sources is written when the phase starts.

## 1. Objective

Let a role be given to a set of people at once, and let that set be maintained by hand, by a rule, or from the identity provider. After this phase an assignment can name a group, groups can contain groups, and membership has a recorded source.

## 2. Requirements covered

AUTH-018 (groups, roles on groups, nested groups), AUTH-019. The group-cycle item of security finding S9. No reference scenario: neither application's documents mention groups, so the scenarios are written from the requirement text.

## 3. Existing implementation affected

| Code | Change |
|---|---|
| `access` assignments | The subject type of milestone 12 gains "group" |
| `access/domain` decision point and permission source | A principal's effective assignments include those of every group they belong to, directly or through nesting |
| Relationship records of milestone 10 | The subject of a relationship may be a group |
| `identity/application/IdentityProviderAdmin` | Gains reading of groups and their members, for synchronization |
| The static separation-of-duties check, when milestone 14 exists | Must see assignments that arrive through groups |

## 4. New functionality delivered

- Groups as stored data, with members that are users, service principals or other groups.
- Nesting, with a cycle refused when a membership is written and a limit on depth.
- Roles assigned to a group, at a scope node and with validity, exactly as to a user.
- Three membership sources, one per group: manual; rule-based, where a developer-written named rule over profile attributes decides; synchronized from the identity provider by a scheduled job.
- The source of every membership is recorded and shown; a synchronized or rule-based group cannot be edited by hand.
- An explanation of why a principal holds a permission now includes the chain of groups.
- An administration API and screens for groups and membership.
- Switches: one for groups as a whole, one each for nesting, rule-based membership and synchronization.

## 5. Modules affected

`platform`, `access`, `identity` (the provider port and its Keycloak adapter), the frontend `access` feature. A scheduled job joins the existing lock-protected jobs.

## 6. Dependencies

Milestone 9 (assignments at scope nodes) and milestone 12 (the subject type). Draft ADR 0056 accepted or replaced, since nested membership is the second place hierarchy queries appear.

## 7. Database changes and migration strategy

Additive, in the `access` folder: a table for groups (with its source), a table for memberships, and, if the storage ADR chooses it, a derived table of effective membership. The assignment subject type gains a value; no existing row changes. With no groups defined, every query returns what it returned before.

## 8. Security implications

- **Cycles under concurrency.** Two administrators can each add a membership that is valid alone and circular together. The write takes a lock, as the last-administrator check does, and a test runs concurrent writers.
- A group is a multiplier: one membership change alters access for a principal across every role the group holds. Membership writes are authorized by their own action, audited, and subject to the "only what you hold" rule for the roles the group carries.
- Synchronization lets an external system grant access. It is off by default, the provider's groups are mapped explicitly and never by matching names, and each run is audited.
- Effective membership, if stored as derived data, is rebuilt and compared in a test; a stale row would be a stale permission.
- Removing someone from a group applies on the next request (AUTH-033).

## 9. Automated tests required

- Scenarios written for this phase: a role on a group reaches its members; a nested group; removal from a group; a group at a scope node.
- Cycle refusal: direct, indirect, and under concurrent writes; the depth limit.
- Each membership source: manual edits allowed only on manual groups; a rule adds and removes members as attributes change; a synchronization run against a real Keycloak container adds and removes members.
- `TwoInstancesIT` extended: a membership change applies on the next request on a second instance.
- The explanation of a permission lists the groups it came through.
- Each switch off: the application starts; with groups off, group assignments grant nothing.
- Query counts against the baseline, with nesting three levels deep.

## 10. Documentation updates

[Authorization](../../architecture/identity/authorization.md), [authentication](../../architecture/identity/authentication.md) (synchronization), glossary entries (group, membership source), recipes for the switches, the [threat model](../../operations/security/threat-model.md), [traceability](../requirements/authorization/traceability.md), [implementation status](implementation-status.md), the groups ADR and an implementation-decisions ADR.

## 11. Acceptance criteria

1. The phase scenarios pass.
2. No sequence of membership writes, concurrent or not, produces a cycle.
3. With all group switches off, every test of the previous phases passes unchanged.
4. A synchronization run is idempotent: running it twice changes nothing the second time.
5. All of the above pass in CI.

## 12. Rollback and recovery

All changes are additive. Turning the groups switch off makes group assignments grant nothing, which narrows access. A bad synchronization run is undone by the next run after the source is corrected, or by turning synchronization off and converting the group to manual; every change it made is in the audit log.

## 13. Usable state at the end of the phase

Administrators can manage access for teams instead of individuals. Applications that define no group are unaffected and pay no extra query.
