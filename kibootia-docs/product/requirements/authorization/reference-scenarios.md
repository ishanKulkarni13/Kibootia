# Authorization: Reference Scenarios

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** planned. No scenario is implemented or tested. Each becomes an automated test in the phase named in the [authorization roadmap](../../roadmap/authorization-roadmap.md).

Part of the [authorization requirements](README.md).

## Purpose and limits

Two applications are used to check that the requirements are generic enough: **Timetia**, the main reference, and **indent-edi**. They **validate** the design. They do not limit it, and nothing of either application's domain is written into Kibootia. The owner decided on 2026-11-11 that no integration of either application with Kibootia is planned, and that indent-edi will be rebuilt from scratch with the same features.

Each scenario is written as an automated test that declares the application's resource types, scopes, relationships and rules in test fixtures only, the way `WidgetFixtures` stands in for a feature module today. A phase is complete when its scenarios pass. A scenario that cannot be expressed without editing foundation code shows that the requirement or the design is wrong.

## Sources

| Application | Where | What was read | What was not |
|---|---|---|---|
| Timetia | `docs` branch of the Timetia repository, `timetia-docs/`: the lecture-rescheduling rules for permissions, the approval model, the ticket lifecycle, shared courses and faculty on leave; the role pages; the review and audit requirements | 15 of the 102 files on that branch | The data-model audit, the flows, the integration and notification documents, the open questions, and the original specification. The branch holds documentation only; there is no Timetia application code |
| indent-edi | `docs/` and `client/lib/server/auth/` in the repository at `d:\projects\EDI`, branch `feat/opti`: decisions DEC-003, 004, 005, 007, 008, 018, 022 and 026, the permissions spec, the open-questions table and the permission matrix source | Those files | The SRS itself, the remaining decisions, and the rest of the code. indent-edi is being rebuilt, so its present code is not a constraint |

Every statement about an application below comes from those files. Anything marked **inferred** is the reviewer's reading and is to be confirmed by the application's owner.

## Timetia

Timetia is an academic scheduling application. Its lecture-rescheduling module lets faculty move, exchange, add, cancel or substitute lectures through tickets, and keeps the base timetable unchanged under a change overlay. Business rules (conflict checks, ticket states, expiry, revision numbers, approval counting) stay in Timetia.

### Principals, resources, scopes

| | |
|---|---|
| Roles | Super Admin, Super HOD, HOD, Faculty, CR (class representative), BR (batch representative), Student |
| Resources | Ticket, timetable and its entries, lecture occurrence, issue (raised by a CR or BR), room, notification settings, audit log of a ticket |
| Scope hierarchy | Department, division, batch. A CR is tied to a division and may be one of several; a BR is tied to a batch |
| Relationships | Faculty teaches a lecture; author of a ticket; requested reviewer of a ticket; the HOD of a department; CR or BR of a division or batch. A shared course belongs to several batches, and its list is seeded manually |
| Attribute rule | CR powers apply to **theory** lectures and BR powers to **lab and tutorial** lectures |
| Time | A ticket expires when the earliest lecture it involves starts. This is a business deadline (AUTH-045), not an authorization expiry |

### Scenarios

| # | Scenario | Requirements |
|---|---|---|
| T1 | **Department authority.** An HOD sees and handles tickets of their own department and not of another. A Super HOD holds the same powers across departments | AUTH-004, 007, 010, 024, 028 |
| T2 | **Faculty act on what they teach.** A faculty member raises tickets only for lectures they teach, and reverts an exchange or substitute applied to their lecture | AUTH-005, 008, 009, 010 |
| T3 | **Representatives by kind of lecture.** A CR reviews, acknowledges and may reject tickets for theory lectures of their division; a BR has the same powers for lab and tutorial lectures of their batch; neither can assign anyone | AUTH-004, 005, 006, 007, 010 |
| T4 | **Reviewer by request.** A person may review and reject a ticket only if the author requested them or their scope covers the ticket. The author may escalate a rejection, which makes the HOD a reviewer | AUTH-005, 009, 013 (eligibility), 042 |
| T5 | **Acting for an absent person.** When a faculty member is on leave the HOD raises, cancels or substitutes on their behalf. In Timetia this is an HOD power, not a time-limited delegation | AUTH-005, 010; AUTH-012 is not required |
| T6 | **State-dependent actions.** A ticket in review may be edited by its author; a rejected one may be edited and resubmitted; an applied one is final and only a new ticket reverts it; nothing is resubmittable once the lecture has started. Timetia owns the states and passes the current state in | AUTH-011, 015, 043 |
| T7 | **Per-ticket audit visibility.** The ticket's log is readable by its author, its requested reviewers, the HOD, the CR or BR within scope and the Super Admin; a student sees the result of a change but not the log | AUTH-014, 022, 044 |
| T8 | **Shared course.** A change to a shared lecture is visible to, and checked for, every batch on its list | AUTH-005, 007, 016 (a record in several scopes) |
| T9 | **Configuration role.** The Super Admin configures rooms, the slot grid, the calendar and notification settings, and is not part of routine approval | AUTH-010, 028 |
| T10 | **Students are read-only.** A student reads the timetable and notifications and cannot open issues or tickets | AUTH-010, 014, 051 |

**Inferred:** in Timetia, "within scope" means the department, division or batch of the ticket's lecture; a ticket therefore belongs to several scope nodes at once (division, batch, department), and for a shared course to several batches.

### What Timetia does not need

From what was read, Timetia needs none of: groups or nested groups, rule-based or synchronized group membership, time-limited delegation (AUTH-012), separation of duties (AUTH-026; the approval model states that self-approval is always allowed and that no case forces a review), permission bundles, administrator-written rules, approval of authorization changes, or multi-tenancy. Quorum and all-reviewer rules (AUTH-013) are not used either: a ticket applies when all **requested** reviewers have approved, and counting is Timetia's business logic.

### What Timetia shows about the requirements

- **Self-approval restrictions (AUTH-013)** must be optional and off by default. Timetia explicitly forbids forcing a review.
- **Ownership is not enough.** The author of a ticket, a requested reviewer and a person in scope are three different relationships to the same record (AUTH-046).
- **A record lies in several scopes at once** (division, batch, department, and several batches for a shared course). The domain model must allow a set of scope nodes per record, not one department.
- **The role list is Timetia's.** Seven roles with these names are application configuration, never Kibootia code.

## indent-edi

indent-edi is an organizational indent (purchase request) application: create, save as draft, submit, list and filter, view, edit drafts, delete drafts, export to Excel, print. Login belongs to another team and approvals to an approval team; both are outside it.

### Principals, resources, scopes

| | |
|---|---|
| Roles (provisional, DEC-004) | Department Employee, Department Head, Purchase Employee, Store Employee, Admin |
| Resources | Indent and its lines, item master, department master, indent-type master |
| Actions | `indent.view`, `indent.create`, `indent.edit`, `indent.submit`, `indent.delete`, `indent.export`, `indent.print` |
| Scopes | `none`, `own` (the creator), `department`, `all`. An indent belongs to a department through its header **or any of its lines**. With several roles the widest scope wins |
| Status rule | Edit, submit and delete need a Draft. This is kept apart from permission: "both must pass". It stays in the application |
| Identity | User identifiers are opaque strings of up to 64 characters |

Its permission matrix is marked provisional: the company has not confirmed it (open question 7 in its own table).

### Scenarios

| # | Scenario | Requirements |
|---|---|---|
| E1 | **Creation.** A role that may create an indent creates one; the creator is the authenticated user, never taken from the request | AUTH-010, 031, 046 |
| E2 | **Draft editing.** An employee edits their own draft; a department head edits drafts of the department; nobody edits a submitted indent. The application supplies the status | AUTH-010, 015, 046 |
| E3 | **Department-scoped visibility.** A list, a search and a filter-options query return only indents within the caller's scope; a record outside it answers as if it did not exist | AUTH-004, 005, 051 |
| E4 | **Several departments on one record.** An indent whose lines name two departments is visible to the head of either | AUTH-007, 016 (a record in several scopes) |
| E5 | **Submission and deletion.** Submit and delete require the permission, the scope and a draft | AUTH-010, 011, 015 |
| E6 | **Export and print.** A user exports or prints only the records within the export scope, which can differ from the view scope; "select all matching" applies the scope to the whole filter | AUTH-014, 048, 049, 051 |
| E7 | **Widest scope wins.** A user holding two roles gets the wider scope for each action | AUTH-008 |
| E8 | **Approval (future).** If the approval team's workflow is integrated, reviewer and approval actions are authorized from application-supplied status | AUTH-011, 013, 042 |

### What indent-edi shows about the requirements

- The scope values `none | own | department | all` are almost exactly what Kibootia has today (`NONE`, `OWN`, `ALL`) plus one hierarchy level. That is a useful check: the first phases must not make the simple case harder.
- **Export has its own scope** in indent-edi's matrix, separate from view. AUTH-049 is therefore needed, not speculative.
- A matrix as application configuration (one file that maps role, action and scope) is what Kibootia's runtime-editable roles replace. indent-edi's approach shows what a small application wants: a few roles, no ceremony. This supports AUTH-040 (a simple application must not be forced to configure advanced capabilities).

## What each application needs

| Capability | Timetia | indent-edi |
|---|---|---|
| Users and roles | Required (seven roles) | Required (five roles) |
| Scoped assignments (department, division, batch) | Required | Required (department) |
| A record in several scopes | Required (shared courses, division and batch) | Required (header and line departments) |
| Resource relationships (teaches, author, requested reviewer) | Required | Only ownership |
| Named conditions (lecture kind; record status) | Required | Required (draft) |
| State-dependent actions | Required | Required |
| Field-level authorization | Not seen in what was read | Not seen in what was read |
| Collection filtering | Required | Required |
| Export authorization | Not seen | Required |
| Audit visibility per record | Required | Not seen |
| Groups, delegation, separation of duties, bundles, tenancy | Not needed | Not needed |

The two applications overlap on scoped assignments, several scopes per record, state-dependent actions and collection filtering. They differ on relationships (Timetia) and export (indent-edi). Neither asks for the larger half of the requirements; those exist because the owner committed to a general foundation (see [ADR 0053](../../../decisions/0053-adopt-authorization-requirements.md)).

## What the scenarios do not cover

Groups, nested groups and membership sources, delegation, separation of duties, permission bundles, the configuration lifecycle, approval of authorization changes, multi-tenancy, service principals and decision caching have **no reference scenario** from either application. For these the roadmap relies on scenario tests written from the requirement text alone, and says so for each phase.
