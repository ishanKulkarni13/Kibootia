# 0059 Data integrity in the database

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** planned. Nothing here is built. Observed: no foreign key exists in any migration, and `user_profile` has a non-unique index on `(tenant_id, email)`.

## Context

Each module owns its tables, and the code keeps to that. Integrity rules, however, live only in code: no table has a foreign key (D-1), email uniqueness is checked in `UserAdministration` with a non-unique index behind it (D-2), event payloads have no version (D-3), failed events are never removed (D-4), and erasing a user while the files module is off leaves their files (D-5). An application that works today can be corrupted by a second writer, a race, or a deployment that skips the code path. Enterprises put the invariants that can be stated in the schema into the schema.

## Decision

1. **Foreign keys inside a module's own tables.** `role_permission.role_id` and `role_assignment.role_id` reference `role.id`; a later module follows the same rule. The role purge deletes children before the parent, as it already does. **No foreign key crosses a module boundary**: links between modules stay plain IDs, because a removable module ([ADR 0024](0024-package-modules-with-infrastructure-toggles.md)) must not leave a constraint behind in another module's table. The convention is added to the [persistence stack](../architecture/data/persistence-stack.md).
2. **Uniqueness the database can enforce, it enforces.** For the invitation race, a unique key on `(tenant_id, email, active_marker)` in `user_profile`, the same technique the other tables use for soft delete. Two cautions the milestone must test: the email is a copy refreshed from the token on each request, so a conflict there must be handled without failing the request; and the address is stored lower-cased only if every writer lower-cases it.
3. **Events carry a schema version.** `EventContext` gains `schemaVersion`, and the rule is that an event record only ever adds optional fields. A golden-file test per event type deserialises a stored JSON sample, so renaming a field fails a test instead of the delivery of events that were already queued. The handler key stays `Class#method`, with a note in the rule that renaming a handler resets its idempotency marks.
4. **`event_publication.serialized_event` becomes `TEXT`.** The 4,000-character limit fails an event that grows (T-6).
5. **Failed events are purged and counted.** An event that has exhausted its attempts is removed after a configurable period (default 90 days), with an audit entry; a gauge reports how many failed events exist, so an alert can fire ([milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md)).
6. **Switching files off after files exist is documented as unsupported, with a guard.** On startup with `kibootia.files.enabled=false`, a check logs a warning and reports it in the health details when `stored_file` has rows, because erasure of those files cannot run. The recipe ([disable files](../recipes/disable-files.md)) says to remove the files first. Making the cleanup run without the module was considered and rejected below.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| No constraints; trust the code | Status quo; the first second writer or migration script breaks it silently |
| Foreign keys across modules too | Breaks removal of `files` and `notes` and ties migrations of one module to another |
| Register the erasure listener even when files is off, deleting only rows | The module's table would then be handled by code that is supposed to be absent; rows would go while objects stay, which is worse than a clear warning |
| A version number per event type in a table | A column on the envelope is enough and travels with the event |

## Consequences

- Migrations must run on an existing database: the new keys need a data check first, and each migration is tested from the previous schema (R-15 of the assessment).
- Soft delete and foreign keys interact: a soft-deleted `role` row still exists, so its children stay until the purge.
- Hibernate `ddl-auto=validate` is unaffected by foreign keys.

## Status

draft. Awaiting the owner. Delivered in [milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md).

## Related

[Architecture assessment](../architecture/architecture-assessment.md) (D-1 to D-5, T-6), [data inventory](../architecture/data/data-inventory.md), [ADR 0012](0012-flyway-managed-schema-evolution.md), [ADR 0033](0033-uuidv7-identifiers.md), [ADR 0034](0034-soft-delete-with-retention-purge.md).
