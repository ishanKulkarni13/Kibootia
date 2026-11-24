# Data Inventory

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** observed. Read from the migrations, `compose.yaml` and the code at commit `75b2091` (2026-11-24). Not machine-checked: a new table, store or retention rule must be added here in the same change.

Where every piece of data lives, who owns it, how long it stays and how it is removed. The rules (tenant column, soft delete, UUIDv7, ownership) are in [persistence stack](persistence-stack.md) and [transactions and data ownership](transactions-and-data-ownership.md); this page holds the facts. Findings against it are in the [architecture assessment](../architecture-assessment.md#data-and-ownership).

## Stores

| Store | Holds | Owner | Survives a restart | Backup in the repository |
|---|---|---|---|---|
| MySQL 8.4, schema `kibootia` (volume `kibootia_mysql_data`) | Every table below | The module named per table | Yes | None |
| PostgreSQL 17 (volume `kibootia_keycloak_db_data`) | Keycloak's realm, users, credentials, sessions | Keycloak. Kibootia never reads it | Yes | None |
| S3-compatible store, RustFS locally (volume `kibootia_rustfs_data`) | File bytes, key `<tenant>/<category>/<file id>/<rendition>` | `files` | Yes | None |
| Browser cookies `kibootia_session.0` to `.7` (chunks of 3,600 bytes) | Encrypted access, refresh and ID tokens, expiry times | The Next.js server (sealed with `SESSION_SECRET`) | Until sign-out or 10 hours | n/a |
| Browser cookie `kibootia_login` | Sign-in state, nonce, PKCE verifier | The Next.js server | 10 minutes | n/a |
| Backend process memory | Rate-limit buckets (Bucket4j and Caffeine), the cached Keycloak admin token | `platform.ratelimit`, `identity` | No; per instance | n/a |
| Next.js process memory | The single-flight map for token refresh | `frontend/src/lib/bff/refresh.ts` | No; per instance | n/a |
| OpenTofu state (volume `kibootia_tofu_state`) | State of the Keycloak realm resources | The `keycloak-config` step | Yes | None; no remote backend |
| Grafana LGTM (volume `kibootia_lgtm_data`, local only) | Traces and metrics | Telemetry | Yes | None |
| Mailpit (local only) | Mail Keycloak sent | Mailpit | No; in memory | n/a |

A backup that covers MySQL, PostgreSQL and the object store **together** is a milestone 6 deliverable ([container deployment](../../operations/deployment/container-deployment.md)). Until it exists, restoring one store without the others leaves rows pointing at missing objects, or Keycloak users without profiles.

## Tables

Every table has `tenant_id` unless the last column says otherwise. "Soft" means `deleted_at` and the retention purge of [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md). No table has a foreign key; links between tables are IDs, and the module that owns the data enforces them in code.

| Table | Module | Content | Personal data | Delete | Retention |
|---|---|---|---|---|---|
| `user_profile` | `identity` | Status, preferences, a read copy of name and email from Keycloak, `avatar_file_id` | Yes: name, email | Soft, then erased by `UserErased` (hard delete) | 30 days after deletion, then erasure begins |
| `role` | `access` | Role name and description, built-in flag | No | Soft | 30 days |
| `role_permission` | `access` | Permission names of a role | No | Hard (with the role purge) | with `role` |
| `role_assignment` | `access` | Which user holds which role, who assigned it | User ID | Hard; removed on `UserErased` | Until unassigned or the user is erased |
| `role_permission_seed` | `access` | Which permissions the built-in `user` role was seeded with | No | None | Permanent |
| `audit_entry` | `audit` | Actor ID, action, target, outcome, trace ID, change summary (at most 4000 characters) | User IDs only; a name or an `@` is rejected | None; append-only | 365 days |
| `stored_file` | `files` | Owner ID, category, content type, rendition names | Owner ID | Soft | 30 days; also removed on `UserErased` |
| `note` | `notes` | Owner ID, title (120), body (4000), `attachment_file_id` | Yes: user-written content | Soft | 30 days; hard-deleted on `UserErased` |
| `event_publication` | `platform` | Serialised events awaiting or finished delivery (`VARCHAR(4000)`). **No `tenant_id` column**; the tenant is inside the JSON | Event payloads: user and note IDs | Completed ones purged | Completed 7 days; **failed ones are never purged** |
| `processed_event` | `platform` | Idempotency marks: event ID and handler name | No | Purged | 30 days |
| `shedlock` | `platform` | Job locks. No `tenant_id` | No | None | Permanent |

## Where a user's data goes when they are erased

`UserAdministration.erase` or the retention job (`UserRetention`) calls `ErasureStarter`, which marks the profile, writes an audit entry and publishes `UserErased`. Three durable handlers then run independently:

| Handler | What it removes |
|---|---|
| `identity` `UserEvents` | The Keycloak account, the role assignments, the profile row |
| `notes` `NoteEvents` | The user's notes |
| `files` `FileEvents` | The user's stored objects, then their `stored_file` rows |

What remains: the user's ID in `audit_entry.actor_id` and `target_id`, in `role_assignment.assigned_by` of other users' rows, and in `created_by` / `updated_by` columns. The audit viewer shows such IDs as erased. **With the files module switched off, `FileEvents` is not registered, so the user's files rows and objects stay** (finding D-5).

## Related

[Persistence stack](persistence-stack.md), [transactions and data ownership](transactions-and-data-ownership.md), [module catalogue](../module-catalogue.md), [audit log](../audit-log.md), [threat model](../../operations/security/threat-model.md).
