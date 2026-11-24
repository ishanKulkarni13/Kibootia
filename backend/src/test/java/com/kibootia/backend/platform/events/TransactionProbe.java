package com.kibootia.backend.platform.events;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Two durable handlers that stand in for a call to another system (ADR 0058): one opts out of the platform's
 * transaction, the other keeps the default. Each says whether a transaction was open while it ran and waits on a latch, so a
 * test can look at the connection pool while the "call" is in flight. Import it into a test; it is a
 * {@code @TestComponent}, so no other test sees it.
 */
@TestComponent
public class TransactionProbe {

	/** An event for the handler that runs without a transaction. */
	public record PlainEvent(String label, EventContext context) implements DomainEvent {

	}

	/** An event for the handler that keeps the platform's transaction. */
	public record DefaultEvent(String label, EventContext context) implements DomainEvent {

	}

	public static final AtomicInteger CALLS = new AtomicInteger();

	public static final AtomicInteger SUCCESSES = new AtomicInteger();

	/** How many more calls fail before one succeeds. */
	public static final AtomicInteger FAILURES_LEFT = new AtomicInteger();

	/** Whether a transaction was open on the handler's thread, as the last handler saw it. */
	public static final AtomicBoolean TRANSACTION_OPEN = new AtomicBoolean();

	/** Counted down when a handler has started; a test waits for it before it looks at the pool. */
	public static volatile CountDownLatch entered = new CountDownLatch(1);

	/** A handler waits for this before it finishes; a test counts it down after it has looked. */
	public static volatile CountDownLatch release = new CountDownLatch(1);

	public static void reset() {
		CALLS.set(0);
		SUCCESSES.set(0);
		FAILURES_LEFT.set(0);
		TRANSACTION_OPEN.set(false);
		entered = new CountDownLatch(1);
		release = new CountDownLatch(1);
	}

	/** Lets handlers run to the end at once, for a test that does not look inside them. */
	public static void doNotWait() {
		release.countDown();
	}

	@DurableEventListener(transactional = false)
	void on(PlainEvent event) {
		run();
	}

	@DurableEventListener
	void on(DefaultEvent event) {
		run();
	}

	private static void run() {
		CALLS.incrementAndGet();
		TRANSACTION_OPEN.set(TransactionSynchronizationManager.isActualTransactionActive());
		entered.countDown();
		try {
			if (!release.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("the test never released the handler");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
		if (FAILURES_LEFT.getAndUpdate((left) -> Math.max(0, left - 1)) > 0) {
			throw new IllegalStateException("the handler fails on purpose");
		}
		SUCCESSES.incrementAndGet();
	}

}
