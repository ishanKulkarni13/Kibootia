package com.kibootia.backend.identity.application;

import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/**
 * An invitation created a Keycloak account, Kibootia's part of the invitation then failed, and removing the account again
 * failed too (ADR 0058). The handler removes the account when the user has no profile, and does nothing when one exists.
 * A durable event, so the cleanup is retried until Keycloak answers instead of being logged and forgotten.
 */
public record InvitationAbandoned(UUID userId, EventContext context) implements DomainEvent {

}
