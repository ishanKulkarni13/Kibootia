package com.kibootia.backend.platform.authorization;

/**
 * A permission a module defines in code (ADR 0044). The name is {@code <module>.<resource>.<action>}, for example
 * {@code identity.users.invite}. A permission whose name ends in {@code -any} widens the one without the suffix from
 * "records I own" to "every record" (see {@link AccessFilter}): {@code notes.notes.update} lets a caller change their
 * own notes, {@code notes.notes.update-any} lets them change anyone's.
 *
 * @param name the permission name
 * @param description what it allows, shown to administrators in the role editor
 * @param grantedToUserRole whether the built-in {@code user} role holds it
 * @param allowedBeforeVerification whether a caller who has not verified their email address may still use it
 *        (ADR 0041); false for everything a module declares unless it says otherwise
 * @param sensitive whether allowed uses are written as decision records too, not only refusals (ADR 0055, point 6)
 */
public record Permission(String name, String description, boolean grantedToUserRole,
		boolean allowedBeforeVerification, boolean sensitive) {

	public Permission(String name, String description, boolean grantedToUserRole) {
		this(name, description, grantedToUserRole, false, false);
	}

	/** A permission the built-in {@code user} role holds. */
	public static Permission forEveryUser(String name, String description) {
		return new Permission(name, description, true);
	}

	/** A permission that must be granted through a role. */
	public static Permission restricted(String name, String description) {
		return new Permission(name, description, false);
	}

	/** The same permission, usable by a caller who has not verified their email address yet. */
	public Permission beforeVerification() {
		return new Permission(this.name, this.description, this.grantedToUserRole, true, this.sensitive);
	}

	/**
	 * The same permission, with its allowed uses recorded as well as its refusals. For actions whose every use should be
	 * reviewable afterwards: erasing, purging, granting.
	 */
	public Permission asSensitive() {
		return new Permission(this.name, this.description, this.grantedToUserRole, this.allowedBeforeVerification, true);
	}

}
