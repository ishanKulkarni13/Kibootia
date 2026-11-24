package com.kibootia.backend.platform.authorization;

import java.util.UUID;

/**
 * Who is acting, as the decision point sees them (ADR 0055). Tokens prove identity only; what a principal may do comes
 * from the decision point.
 *
 * <p>Two kinds exist today. A service principal for machine clients is planned (milestone 12); adding it is adding a
 * case here, which is why the type is sealed: every {@code switch} over it must then handle the new kind.
 */
public sealed interface Principal permits Principal.User, Principal.System {

	/** The ID that audit columns and decision records name: the Keycloak user ID, or the nil UUID for the system. */
	UUID id();

	/** The tenant the work runs in. */
	UUID tenantId();

	/** A stable label for logs, metrics and decision records. */
	String kind();

	/**
	 * A signed-in person.
	 *
	 * @param id the Keycloak user ID ({@code sub})
	 * @param emailVerified whether the address is verified, as the token says (ADR 0041)
	 */
	record User(UUID id, UUID tenantId, boolean emailVerified) implements Principal {

		@Override
		public String kind() {
			return "user";
		}

	}

	/**
	 * Work that no user started: a job or an event handler, entered only through {@link Authorization#runAsSystem}.
	 *
	 * @param reason why system authority is used; it is required, and every decision record of the work names it
	 */
	record System(UUID tenantId, String reason) implements Principal {

		/** The ID the system acts under in audit columns ({@code created_by}, {@code actor_id}). */
		public static final UUID ID = new UUID(0L, 0L);

		public System {
			if (reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("A reason is required to act as the system");
			}
		}

		@Override
		public UUID id() {
			return ID;
		}

		@Override
		public String kind() {
			return "system";
		}

	}

}
