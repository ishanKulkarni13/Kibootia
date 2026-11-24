package com.kibootia.backend.platform.events;

import java.time.Instant;
import java.util.UUID;

/**
 * What travels inside every durable event (ADR 0039): the registry stores an event as serialised data, so the tenant
 * and the trace cannot ride on the thread that delivers it. Create one with {@link EventContextFactory#capture()}.
 *
 * @param eventId identifies the event; handlers use it to skip a repeated delivery
 * @param tenantId the tenant the event happened in; restored before the handler runs
 * @param traceparent the W3C trace context of the publisher, or null when it was not traced
 * @param occurredAt when the event was raised
 * @param schemaVersion the version of the event's shape when it was raised (ADR 0059): {@link #CURRENT_VERSION} for a new
 * event, 0 for one that was queued before versions existed, which is read as version 1. An event record only ever adds
 * optional components, so a handler can read an event that was written by an older version of the code; a golden-file
 * test per event type keeps that true.
 */
public record EventContext(UUID eventId, UUID tenantId, String traceparent, Instant occurredAt,
		Integer schemaVersion) {

	/** The shape events are written in today. Raise it only with a rule for how a handler reads the older one. */
	public static final int CURRENT_VERSION = 1;

	/**
	 * A wrapper, not an {@code int}, on purpose: Jackson 3 refuses a missing value for a primitive component, so an event
	 * that was queued before this component existed would fail to be read. A missing version is 0.
	 */
	public EventContext {
		if (schemaVersion == null) {
			schemaVersion = 0;
		}
	}

}
