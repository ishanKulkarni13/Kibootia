package com.kibootia.backend.platform.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Marks a method that handles a {@link DomainEvent} after the publisher's transaction commits, on another thread, with
 * retries (ADR 0031). The first parameter is the event. Before the method runs the platform
 *
 * <ul>
 * <li>binds the tenant and continues the publisher's trace from the event's {@link EventContext};</li>
 * <li>runs the method as the system, because no user is acting;</li>
 * <li>opens the method's own transaction and skips the call when this handler already processed this event, so a
 * repeated delivery is harmless. If the method throws, the transaction and the "processed" mark roll back, and the
 * registry keeps the event for a retry.</li>
 * </ul>
 *
 * Use it instead of {@code @ApplicationModuleListener}, which would open its transaction before the tenant is known
 * (an architecture rule enforces this). The method must be idempotent anyway where it touches other systems: delivery
 * is at least once, not exactly once.
 *
 * <p><b>A handler that calls another system.</b> No database transaction may be open while Keycloak, object storage or
 * any other system outside the database is being called (ADR 0058): a slow server would hold a connection from a small
 * pool. Such a handler declares {@code transactional = false}. The platform then reads the "already handled" mark in a
 * short transaction, runs the method with no transaction, and records the mark in a short transaction afterwards. The
 * method opens short transactions of its own for its database work, and a crash between the effect and the mark repeats
 * the method, which is safe because it is idempotent. An architecture rule fails the build for a handler in a class that
 * uses {@code IdentityProviderAdmin} or {@code ObjectStorage} and does not do this.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Async
@TransactionalEventListener
public @interface DurableEventListener {

	/**
	 * Whether the platform wraps the method and the "already handled" mark in one transaction (the default). Set it to
	 * false for a method that calls a system outside the database; see above.
	 */
	boolean transactional() default true;

}
