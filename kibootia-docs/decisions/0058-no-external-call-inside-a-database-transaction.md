# 0058 No external call inside a database transaction

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** built and verified (CI run 45522177552, 2026-11-25). Observed in `DurableEventListener` (`transactional = false`), `DurableEventInterceptor`, `RetentionPurgeable.ownsTransactions`, `UserEvents`, `FileEvents`, `FileRetention`, `KeycloakAdminAdapter` and `ExternalCallRules`.

## Context

`FileService` states the rule: "no database transaction is ever open while the storage server is being called". Three places break it (T-1, T-2 in the [architecture assessment](../architecture/architecture-assessment.md)):

- `FileRetention.purge` is `@Transactional` and deletes up to 20 batches of objects from S3 inside that transaction.
- `DurableEventInterceptor` runs every durable handler in a `REQUIRES_NEW` transaction that also holds the idempotency mark, so `UserEvents` calls Keycloak (find, enable, end sessions, delete, email) with a connection and a row lock held, and `FileEvents` calls S3 the same way.
- `UserAdministration.invite` creates the Keycloak account first and compensates with a delete if Kibootia's part fails. If the delete fails it is only logged, which leaves an orphan Keycloak account.

The cost is not theoretical: a slow Keycloak or storage server holds database connections from a small pool, and a request waiting for a connection waits for Keycloak. The handlers are already written to be repeated (delivery is at least once), which makes the fix cheap.

## Decision

1. **Rule.** No code holds a database transaction open across a call to Keycloak, object storage or any other system outside the database. A new architecture rule (13) checks the part that can be checked: a class that depends on `IdentityProviderAdmin` or `ObjectStorage` has no `@Transactional` method.
2. **Durable handlers can opt out of the surrounding transaction.** `@DurableEventListener` gains an attribute, `transactional = false`. For such a handler the interceptor reads the idempotency mark in a short transaction, runs the handler with no transaction (the handler opens short ones of its own for its database work), and records the mark in a short transaction afterwards. A crash between the handler and the mark repeats the handler, which is safe because handlers are idempotent by rule 11 of [architecture enforcement](../architecture/architecture-enforcement.md). `UserEvents` and `FileEvents` use it. Handlers that only touch the database keep the current behaviour, where the mark and the effects commit together.
3. **The retention job does not wrap `purge()` in a transaction.** `FileRetention` reads a batch, deletes the objects, then deletes the rows in a short transaction, as its own comment says it intends ("the stored objects first, then the rows"). A failure in between leaves rows that name objects that are gone; the next run finishes.
4. **Keycloak calls get bounded retry, not a circuit breaker, for now.** Idempotent calls (find, set enabled, end sessions, delete) retry a small fixed number of times with jittered back-off on connection errors and 5xx, using the retry support in Spring Framework 7 if it covers this, or a small hand-written loop otherwise (to be checked in milestone 5.1 before a dependency is added). A circuit breaker is not added: the asynchronous paths are already retried by the outbox, and the synchronous paths (first sign-in, invitation) should fail fast with 503, which they do. Revisit with the metrics of milestone 5.1.
5. **The compensating delete becomes durable.** When creating a Keycloak account succeeds and Kibootia's part fails, the cleanup is recorded for retry, and a reconciliation job (as `files` has) removes Keycloak accounts that have no profile after a grace period.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Keep the transaction and raise the connection pool | Hides the problem until an outage; one slow dependency still blocks every request |
| Call the external system after commit from a plain listener | Loses the retry and idempotency that `@DurableEventListener` gives |
| Resilience4j with a circuit breaker on every Keycloak call | A dependency with a purpose that is not yet shown; the retry above covers the failures seen, and the breaker can be added with a measured need |
| Make every handler non-transactional | Handlers that only write to the database are better with the mark and the effect in one commit |

## Consequences

- Handlers with `transactional = false` can run their effect twice if the process dies after it and before the mark. This is already the contract of every handler; tests must repeat each such handler (`UserEvents` already has a test that delivers the same account change again).
- `DurableEventInterceptor` has two paths to test, and rule 11's tenant restoration applies to both.
- The retention purge no longer rolls back as one unit. Each batch is its own commit, which is what `MAX_BATCHES` already assumes.

accepted on the owner's delegation of 2026-11-25. Delivered in [milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md).

**Changes made while building** (2026-11-25):

- **Item 1, the rule, is written about calls, not classes.** "A class that depends on `IdentityProviderAdmin` or `ObjectStorage` has no `@Transactional` method" would also flag `UserAdministration` and `ProfileService`, whose transactional methods only touch the database (the Keycloak calls happen in the handlers they raise events for). Rule 13 has two halves in `ExternalCallRules`: a `@Transactional` method, or a method of a `@Transactional` class, does not call either port; and a `@DurableEventListener` in a class that holds either port declares `transactional = false`. Both are shown to fire by `ExternalCallRulesTest`. Like rule 7, the first half does not see a call that reaches the port through another method. A new port is added to `ExternalCallRules.OTHER_SYSTEMS`.
- **Item 1 is also checked at run time.** The test fakes for Keycloak and for storage record every call made while a transaction is open on the calling thread, and `AdminApiTest` fails any integration test in which that happened. This is how the rule's blind spot (indirect calls) is covered.
- **Item 2.** `@DurableEventListener(transactional = false)` is built as described. The handler's own database work is done in short transactions it opens itself; `UserEvents.on(UserErased)` does the roles, the profile and the audit entry in one. `HandlerTransactionIT` shows, with the connection pool's own count, that such a handler holds no connection while it works and that a default handler holds one, that a failure leaves no mark and the retry runs it again, and that an event handled before is skipped. `UserEvents` and `FileEvents` use it.
- **Item 3.** The retention job asked every purgeable to run inside one transaction, so removing `@Transactional` from `FileRetention` was not enough. `RetentionPurgeable.ownsTransactions()` (default false) lets a purge say it opens its own; `FileRetention` does, and the job then calls it with none open. The files store runs one self-contained statement at a time, so no further change was needed there.
- **Item 4.** The retry uses `org.springframework.core.retry` in Spring Framework 7.0 (`RetryTemplate`, `RetryPolicy`), so no dependency was added. `find`, enabling or disabling, ending sessions and deleting an account are repeated after a connection error or a 5xx or 429 answer, `kibootia.identity.keycloak.retries` times (default 2) with a doubling wait from `retry-delay` (default 200 ms) and some jitter; a 404 is an answer and is not repeated. Creating an account and sending an email are never repeated, because a repeat would make a second one. `KeycloakAdminAdapterRetryTest` runs the adapter against a local HTTP server standing in for Keycloak.
- **Item 5, the compensating delete, is built as an event, not as a sweep.** When an invitation fails after Keycloak created the account and removing it fails too, the removal is recorded as the durable event `InvitationAbandoned`, whose handler deletes the account unless a profile exists for it. A reconciliation job that removes Keycloak accounts "with no profile after a grace period" is **not built**: a person who registers by themselves has a Keycloak account and no profile until their first sign-in, so "no profile" does not identify an orphan and the job would delete real people. It can be built if invitation accounts are marked in Keycloak, which needs a user-profile attribute in the realm. What is left uncovered is the case where the database is down at the moment the account has to be taken back and Keycloak refuses the delete too: the log names the account and it must be removed in Keycloak by hand.

## Related

[Architecture assessment](../architecture/architecture-assessment.md) (T-1, T-2, T-3), [ADR 0031](0031-transactional-outbox-and-locked-jobs.md), [ADR 0034](0034-soft-delete-with-retention-purge.md), [ADR 0051](0051-milestone-4-implementation-decisions.md).
