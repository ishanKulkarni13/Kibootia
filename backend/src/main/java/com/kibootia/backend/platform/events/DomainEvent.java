package com.kibootia.backend.platform.events;

/**
 * An event that must not be lost (ADR 0031). Publish it with {@code ApplicationEventPublisher.publishEvent} in the same
 * transaction as the change; the registry keeps it until a {@link DurableEventListener} has handled it. The record must
 * serialise to JSON and keep the context in a component named {@code context}, because the failed-event view finds the
 * tenant there.
 *
 * <p><b>Changing an event.</b> A queued event is stored as JSON and is read by whatever code is running when it is
 * delivered, which may be newer than the code that raised it (ADR 0059). An event record therefore only ever <em>adds</em>
 * components, and the new component must be safe when absent: a nullable type, never a primitive, because Jackson 3 fails
 * on a missing value for a primitive component (see {@link EventContext#schemaVersion()}, which is an {@code Integer}
 * for that reason). Never rename, retype or remove one; raise a new event type instead. Each event type has a golden file in
 * {@code src/test/resources/events} that the current code must still read. The handler's key in the "already handled"
 * marks is {@code Class#method}: renaming a handler method makes events that were already handled run again.
 *
 * <pre>{@code
 * record NoteDeleted(UUID noteId, EventContext context) implements DomainEvent { }
 * }</pre>
 */
public interface DomainEvent {

	EventContext context();

}
