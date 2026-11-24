# 0058 No external call inside a database transaction

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** planned. Nothing here is built. The behaviour it changes is observed in `FileRetention.java`, `DurableEventInterceptor.java` and `UserEvents.java`.

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

## Status

draft. Awaiting the owner. Delivered in [milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md).

## Related

[Architecture assessment](../architecture/architecture-assessment.md) (T-1, T-2, T-3), [ADR 0031](0031-transactional-outbox-and-locked-jobs.md), [ADR 0034](0034-soft-delete-with-retention-purge.md), [ADR 0051](0051-milestone-4-implementation-decisions.md).
