# Changelog

All notable changes to Kibootia. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
versions follow [Semantic Versioning](https://semver.org/). Each version has three kinds of note that a fork needs:
**Changed** (what the application does differently), **Upgrade** (what a fork that has copied the repository must do to take
the version) and **Security** (what was found or fixed). Commits follow Conventional Commits, so the history has the detail.

## [Unreleased]

First release in preparation (milestone 6). What is in `main` so far:

### Added

- A decision contract with reasons for every decision, grants with a reach, deny grants behind a switch, and an optional Open
  Policy Agent guardrail that can only narrow what roles allow (milestones 7 and 8, ADRs 0053 to 0055, 0064 to 0066).
- Architecture hardening (milestone 5.1): module dependencies declared and checked, foreign keys inside a module, versioned
  events with golden files, failed-event retention and a gauge, no database transaction across a call to Keycloak or object
  storage (rule 13), a bounded retry for the Keycloak calls that are safe to repeat, and a durable cleanup of a failed
  invitation (ADRs 0057 to 0059).
- Edge hardening of the Next.js server: a Content-Security-Policy with a nonce (report-only by default), HSTS, `__Host-`
  cookies over https, a key ring for the session secret, a timeout and a size limit on the `/api` proxy, and a production
  start that refuses to fall back to localhost (ADR 0063).
- CI: coverage reports, CodeQL, dependency review, a software bill of materials per image, infrastructure and documentation
  checks, every action pinned to an upstream commit (ADR 0061).
- Administration: a screen for failed events, deny grants in the role editor, and actions only the system performs
  (ADR 0067).
- The Apache License, Version 2.0 ([ADR 0068](kibootia-docs/decisions/0068-apache-2-0-licence.md)).

### Upgrade

- `SESSION_SECRET` may now list several keys separated by commas, newest first. A single key works as before. In production
  `PUBLIC_BASE_URL`, `KEYCLOAK_ISSUER` and `API_BASE_URL` must be set: nothing falls back to localhost.
- Over https the session and login cookies are named `__Host-kibootia_session` and `__Host-kibootia_login`, so everyone signs
  in once more after the upgrade.
- New migrations in `access` (foreign keys; rows of roles that no longer exist are removed first) and `platform`
  (`event_publication.serialized_event` becomes `TEXT`). They run at startup.
- `EventContext` has a `schemaVersion`. Events that were queued before the upgrade are still read (their version is 0).
- A handler that calls another system must declare `@DurableEventListener(transactional = false)`; architecture rule 13 fails
  the build otherwise.
- The purges that jobs run are actions only the system performs: they no longer appear in the role editor, and a grant of one
  is refused. Rows that already exist are ignored.

### Security

- Role assignment requires holding the permissions of the role given (milestone 5.1, closes finding S1).
- The Trivy action in CI was pinned to a commit that is not part of the upstream repository; it is replaced by the signed
  upstream release (ADR 0061).
