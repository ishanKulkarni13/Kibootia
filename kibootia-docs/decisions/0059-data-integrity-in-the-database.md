# 0059 Data integrity in the database

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** items 1 and 3 to 6 built on branch `m-5-1-data` (observed; CI run pending); item 2 is not built, and why is under Status. Observed in `V202611250900__access_role_foreign_keys.sql`, `V202611250910__platform_event_publication_text.sql`, `EventContext`, `FailedEventRetention`, `FailedEventsMetrics` and `FilesDisabledHealthIndicator`.

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

accepted on the owner's delegation of 2026-11-25. Delivered in [milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md).

**Changes made while building** (2026-11-25):

- **Item 1.** `role_grant` already had its key (milestone 8). The migration adds the two that were missing, on `role_permission` and `role_assignment`, and first removes rows whose role no longer exists: they grant nothing, because every read joins the role. `RoleForeignKeysIT` runs the migration's own script against such rows.
- **Item 2 is not built.** The profile's email is a copy that is refreshed from the token when its owner next signs in. Keycloak refuses a second account with an address another account holds (the realm sets `duplicate_emails_allowed = false`), so the invitation race the item names is already closed where the account is created: the second request gets `EmailInUse`, which the application turns into 409. The copies can differ for a while, though: if A changes their address and B then takes the old one, B's profile is written with an address A's profile still holds until A signs in again. A unique key would turn that ordinary moment into a failed sign-in or a failed profile creation, and handling it (leaving the copy stale, or clearing another person's copy) adds risk to the sign-in path for no gain over the check that exists. The check in `UserAdministration` stays as a courtesy to the person inviting.
- **Item 3.** `EventContext.schemaVersion` is 1 for a new event and 0 for one queued before versions existed, which a handler reads as version 1. The component is an `Integer`, not an `int`: Jackson 3 (Spring Boot 4) refuses a missing value for a primitive component, which the test below showed on its first run, and every event queued before this change would have failed to be read. `EventSchemaCompatibilityIT` finds every event type in the application, requires a golden file named after the class in `src/test/resources/events`, and requires that the current code reads every component of it. The rule for changing an event is in the Javadoc of `DomainEvent`.
- **Item 4.** `serialized_event` is `TEXT`. Hibernate's validation at startup accepts it (every integration test starts the application), and `DurableEventsIT` publishes a 6,000-character event.
- **Item 5.** The purge is `FailedEventRetention` in the `audit` module, because the platform cannot write an audit entry. It removes events with status FAILED whose attempts are at least `kibootia.events.max-attempts` and which were published more than `kibootia.events.failed-retention-days` ago (default 90), in the current tenant, and records `audit.failed-events.purged` with the number removed. It has its own permission, `audit.failed-events.purge`. The gauge is `kibootia.events.failed`: every event that has failed and not been delivered since, in every tenant, because an alert is not per tenant.
- **Item 6.** `FilesDisabledHealthIndicator` exists only while `kibootia.files.enabled=false`. It logs a warning at startup and reports `storedFiles` and a warning in the health details when `stored_file` has rows. Health stays UP: the application works, it only keeps what it can no longer clean up. Whether the details are visible depends on the management endpoint's `show-details` setting.

## Related

[Architecture assessment](../architecture/architecture-assessment.md) (D-1 to D-5, T-6), [data inventory](../architecture/data/data-inventory.md), [ADR 0012](0012-flyway-managed-schema-evolution.md), [ADR 0033](0033-uuidv7-identifiers.md), [ADR 0034](0034-soft-delete-with-retention-purge.md).
