# Enable deny grants

- **Type:** recipe
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** observed on branch `m-8` (`DenyGrantsIT` starts the application with the switch on; local run, 2026-11-24; CI run pending). Documented, not verified, until CI covers it.

## Problem

A role takes away nothing: if any of a user's roles allows an action, they hold it. When one person in a group must not do one thing the group may do, the only tool is to split roles. A deny grant takes the action away whatever other roles give ([ADR 0054](../decisions/0054-authorization-domain-model.md), rule 5; [ADR 0065](../decisions/0065-milestone-8-implementation-decisions.md)).

## When to use it, and when not to

Use it for exceptions: one role that must never purge, whatever else its holders are given. Prefer narrower roles when they are enough: a deny is a second place to look when asking why someone may not do something. The decision records name the deny that decided (`authorization.denied_by_grant`).

## Prerequisites and compatibility

Milestone 8 or later.

## Steps

1. Set `kibootia.access.deny-grants-enabled=true` (environment `KIBOOTIA_ACCESS_DENY_GRANTS_ENABLED=true`) and restart the backend. `GET /api/v1/permissions` then answers `"denyGrantsEnabled": true`.
2. Create or edit a role through the API with a grant whose `effect` is `DENY`, for example `{"action":"notes.notes.delete","reach":"all","effect":"DENY"}`. The role editor shows a role's deny grants and keeps them on save; it does not add them.
3. Assign the role. From the next request on, the action is refused to its holders, with reason `authorization.denied_by_grant`.

## Configuration and secrets

`kibootia.access.deny-grants-enabled` (no secret).

## Security

- A deny always reaches every record; a deny of "only my own" is refused, because a list cannot yet exclude records.
- Editing a role to add a deny requires holding the action; it cannot give anyone access.
- Switching the setting off again stops new deny grants from being added but keeps applying the existing ones, so it never widens access. Remove them from the roles first if that is the intent.

## Removal

Remove the deny grants from every role, then set the switch back to `false`.
