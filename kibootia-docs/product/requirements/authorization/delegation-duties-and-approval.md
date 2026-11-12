# Authorization: Delegation, Time Limits, Duties and Approval

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. Nothing in this document exists in the repository today. Per-requirement status: [traceability](traceability.md).

Part of the [authorization requirements](README.md).

## Delegation and time limits

### AUTH-012 Delegation and acting authority

**Status:** Approved

The system supports controlled delegation of authority. Examples: a department administrator delegating a responsibility; a department head temporarily acting for an absent person; a coordinator transferring a defined responsibility; a reviewer assigning an authorized substitute where permitted.

A delegation defines the delegator, the delegate, the scope, the permitted actions, the validity period, the applicable constraints and the revocation conditions.

Delegated authority never exceeds the delegator's own authority. Delegation is auditable.

> **Review note:** the audit log today records the acting user only and deliberately cannot record an action on someone else's behalf. Delegation needs both identities in every audit entry and decision record (who acted, under whose authority). It also needs a rule for what happens to a delegation when the delegator later loses the authority; recommended: the delegation stops working at once, because it is evaluated against the delegator's current authority on every decision.

### AUTH-021 Time-bound authorization

**Status:** Approved

Assignments and permissions can be time-bound. The system supports a start date and time, an expiry date and time, automatic expiration, manual revocation, authorized renewal and extension, scope-specific validity, and correct time-zone handling.

An expired assignment no longer authorizes access, even while its record remains stored. Grants, renewals, extensions and revocations are auditable.

### AUTH-045 Authorization validity versus business deadlines

**Status:** Proposed

The system distinguishes authorization validity from business deadlines.

- **Authorization validity** is whether a principal's role, assignment, delegation or permission is currently valid.
- **A business deadline** is whether an application operation is still valid under its domain rules.

Examples: a temporary administrator assignment expires at a set date and time (authorization validity). A Timetia rescheduling ticket expires when the earliest affected lecture begins, and an indent submission deadline is set by the application (business deadlines).

Kibootia provides time-aware authorization. The consuming application owns its business deadlines and the lifecycle expiry of its resources. The authorization system does not treat a business deadline as an authorization expiry.

## Separation of duties

### AUTH-026 Separation of duties

**Status:** Approved

The system supports static and dynamic separation of duties.

- **Static:** incompatible roles or responsibilities cannot be assigned to the same principal, where the application defines such a restriction.
- **Dynamic:** incompatible actions cannot be performed together or within a defined operational context, even when the user holds both roles. Example: a requester cannot approve their own request; the creator of a transaction cannot approve it.

Applications define conflicting roles, conflicting responsibilities, restricted action combinations, applicable scopes, relevant contexts and permitted exceptions.

### AUTH-041 Application-defined exceptions to separation of duties

**Status:** Proposed

Applications should be able to define explicit exceptions to a separation-of-duties restriction for specific actions, resources and contexts. For example, a user may perform a limited action on a resource they created while a separate authority is still required for more sensitive operations.

An exception is explicitly configured, limited to a defined scope, restricted to permitted actions, auditable, and unable to weaken an unrelated restriction.

> **Review note on dynamic separation of duties:** "this user created this record" is a fact about the record, held by the application. The decision point can enforce the rule only if the application supplies that fact (as an attribute or a relationship such as `creator`). The authorization system keeps no history of who did what to a business record.

## Approval-related capabilities

### AUTH-013 Multi-reviewer and approval authorization

**Status:** Approved

The system supports the authorization needs of applications with several reviewers or approval responsibilities. Applications can define authorization conditions for eligible reviewers, required reviewers, optional reviewers, all-reviewer approval, any-reviewer approval, quorum-based authorization, rejection authority, escalation authority, self-approval restrictions and context-specific reviewer permissions.

Kibootia provides authorization primitives. The consuming application's workflow owns the approval process.

> **Evidence from Timetia:** its approval model states that self-approval is always allowed and that no case forces a review, so "self-approval restrictions" must be an optional rule that is off unless an application turns it on. A ticket there applies when the requested reviewers have all approved; that count is Timetia's workflow, as the review note below recommends.
>
> **Review note:** the list mixes two kinds of thing. "Is this person an eligible reviewer for this record", "may they reject", "may they escalate" and "may they approve their own request" are authorization questions about one principal and one action. "Have all reviewers approved", "has any reviewer approved" and "is the quorum reached" are counts over the workflow's history. A decision point that answers the second kind must store and count approvals, which makes it a workflow engine and breaks the responsibility boundary. Recommended reading: Kibootia answers the first kind; for the second kind the application counts and, if it wants a rule such as "may finalize only when the quorum is reached", it passes the count in as an attribute.

### AUTH-042 Reviewer eligibility and optional or required status

**Status:** Proposed

Applications should be able to define reviewer eligibility using roles, attributes, relationships, scope, resource assignment and workflow context, and to distinguish mandatory from optional reviewer responsibilities.

The authorization system can evaluate whether a particular principal is authorized to perform a reviewer action. The consuming application selects reviewers, tracks progress and decides when the workflow is complete.
