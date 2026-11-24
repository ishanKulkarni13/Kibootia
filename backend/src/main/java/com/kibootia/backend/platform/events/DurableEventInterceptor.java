package com.kibootia.backend.platform.events;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Wraps every {@link DurableEventListener} call (ADR 0031, ADR 0039). In order: bind the event's tenant, continue its
 * trace, act as the system, open a new transaction, skip the call if this handler already processed the event, then run
 * the handler. The tenant is bound before the transaction because Hibernate reads it when the session opens.
 *
 * <p>Dependencies are looked up on first use: this interceptor is created while the proxy infrastructure starts, before
 * the beans it needs exist.
 */
final class DurableEventInterceptor implements MethodInterceptor {

	private static final Logger log = LoggerFactory.getLogger(DurableEventInterceptor.class);

	private final BeanFactory beans;

	DurableEventInterceptor(BeanFactory beans) {
		this.beans = beans;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Object[] arguments = invocation.getArguments();
		if (arguments.length == 0 || !(arguments[0] instanceof DomainEvent event)) {
			throw new IllegalStateException(invocation.getMethod() + " is a @DurableEventListener, so its first "
					+ "parameter must be a DomainEvent");
		}
		EventContext context = event.context();
		String eventType = event.getClass().getSimpleName();
		String handler = ClassUtils.getUserClass(invocation.getThis()).getName() + "#" + invocation.getMethod().getName();
		boolean transactional = isTransactional(invocation);
		try {
			return TenantContext.callAs(context.tenantId(), () -> inSpan(context, eventType,
					() -> this.beans.getBean(Authorization.class)
						.runAsSystem("handling " + eventType + " in " + handler,
								() -> transactional ? inNewTransaction(invocation, context, eventType, handler)
										: withoutTransaction(invocation, context, eventType, handler))));
		}
		catch (Unchecked wrapped) {
			throw wrapped.getCause();
		}
	}

	/** Whether the handler asked to run in the platform's transaction; the default. See {@link DurableEventListener}. */
	private static boolean isTransactional(MethodInvocation invocation) {
		Class<?> target = (invocation.getThis() != null) ? AopUtils.getTargetClass(invocation.getThis()) : null;
		DurableEventListener annotation = AnnotatedElementUtils.findMergedAnnotation(
				AopUtils.getMostSpecificMethod(invocation.getMethod(), target), DurableEventListener.class);
		return annotation == null || annotation.transactional();
	}

	/**
	 * For a handler that calls another system (ADR 0058): the mark is read in a short transaction, the method runs with
	 * none, and the mark is written in a short transaction afterwards. A failure leaves no mark, so the registry's retry
	 * runs the method again; so does a crash between the method and the mark. The method must therefore be idempotent,
	 * as every handler must.
	 */
	private Object withoutTransaction(MethodInvocation invocation, EventContext context, String eventType,
			String handler) {
		ProcessedEvents marks = this.beans.getBean(ProcessedEvents.class);
		TransactionTemplate read = shortTransaction();
		read.setReadOnly(true);
		if (Boolean.TRUE.equals(read.execute((status) -> marks.isProcessed(context.eventId(), handler, context.tenantId())))) {
			log.info("Skipping {} {} in {}: already processed", eventType, context.eventId(), handler);
			return null;
		}
		Object result;
		try {
			result = invocation.proceed();
		}
		catch (Throwable ex) {
			throw new Unchecked(ex);
		}
		shortTransaction().executeWithoutResult(
				(status) -> marks.markProcessed(context.eventId(), handler, context.tenantId()));
		return result;
	}

	private TransactionTemplate shortTransaction() {
		TransactionTemplate transaction = new TransactionTemplate(this.beans.getBean(PlatformTransactionManager.class));
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return transaction;
	}

	private Object inNewTransaction(MethodInvocation invocation, EventContext context, String eventType,
			String handler) {
		TransactionTemplate transaction = new TransactionTemplate(this.beans.getBean(PlatformTransactionManager.class));
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return transaction.execute((status) -> {
			if (!this.beans.getBean(ProcessedEvents.class).markProcessed(context.eventId(), handler,
					context.tenantId())) {
				log.info("Skipping {} {} in {}: already processed", eventType, context.eventId(), handler);
				return null;
			}
			try {
				return invocation.proceed();
			}
			catch (Throwable ex) {
				// The template rolls back on an unchecked exception; the original is rethrown unchanged by invoke.
				throw new Unchecked(ex);
			}
		});
	}

	private <T> T inSpan(EventContext context, String eventType, java.util.function.Supplier<T> work) {
		Tracer tracer = this.beans.getBeanProvider(Tracer.class).getIfAvailable();
		if (tracer == null) {
			return work.get();
		}
		Span.Builder builder = tracer.spanBuilder().name("event " + eventType);
		TraceContext parent = parentOf(tracer, context.traceparent());
		if (parent != null) {
			builder.setParent(parent);
		}
		Span span = builder.start();
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			return work.get();
		}
		catch (RuntimeException ex) {
			span.error(ex);
			throw ex;
		}
		finally {
			span.end();
		}
	}

	/** Reads a W3C {@code traceparent} ({@code 00-<trace id>-<span id>-<flags>}); anything else means no parent. */
	private static TraceContext parentOf(Tracer tracer, String traceparent) {
		if (traceparent == null) {
			return null;
		}
		String[] parts = traceparent.split("-");
		if (parts.length != 4 || parts[1].length() != 32 || parts[2].length() != 16) {
			return null;
		}
		try {
			boolean sampled = (Integer.parseInt(parts[3], 16) & 1) == 1;
			return tracer.traceContextBuilder().traceId(parts[1]).spanId(parts[2]).sampled(sampled).build();
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	/** Carries the handler's own exception through the transaction template, which only accepts unchecked ones. */
	private static final class Unchecked extends RuntimeException {

		Unchecked(Throwable cause) {
			super(cause);
		}

	}

}
