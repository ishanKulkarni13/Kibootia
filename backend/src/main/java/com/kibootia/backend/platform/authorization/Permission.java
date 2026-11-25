package com.kibootia.backend.platform.authorization;

import java.util.Objects;

/**
 * An action a module declares in code (ADR 0044, ADR 0054); administrators see it as a permission in the role editor and
 * grant it with a reach. The name is {@code <module>.<resource>.<action>}, for example {@code identity.users.invite}.
 *
 * <p>Two kinds (ADR 0055, point 5):
 * <ul>
 * <li>an action <strong>on the collection</strong> ({@link #onCollection}): creating, an administrative action, anything
 * decided without a record. Its only reach is {@link Reach#ALL}, so it may be checked with {@link RequiresPermission};</li>
 * <li>an action <strong>on a record</strong> ({@link #onRecord}) of a declared {@link ResourceType}: it can be granted
 * for every record or, by the type's relations, only for some, so it is checked with the record, never with
 * {@code @RequiresPermission}. Startup refuses the annotation on such an action.</li>
 * </ul>
 *
 * @param resourceType the resource type of an action on a record, or null for an action on the collection
 * @param userRoleReach the reach the built-in {@code user} role is offered this action with, or null when it is not
 * @param allowedBeforeVerification whether a caller who has not verified their email address may still use it (ADR 0041)
 * @param sensitive whether allowed uses are written as decision records too, not only refusals (ADR 0055, point 6)
 * @param systemOnly whether only the system may do it: the purge that a job runs, for example. Such an action is declared
 * so that the code can check it like any other, but it is not offered in the role editor, no role can hold it, and not even
 * the administrator role does; the system is allowed everything (ADR 0067)
 */
public record Permission(String name, String description, String resourceType, Reach userRoleReach,
		boolean allowedBeforeVerification, boolean sensitive, boolean systemOnly) {

	public Permission {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		if (resourceType == null && userRoleReach != null && !(userRoleReach instanceof Reach.All)) {
			throw new IllegalArgumentException(name + " is an action on the collection; its only reach is 'all'");
		}
		if (systemOnly && (userRoleReach != null || allowedBeforeVerification)) {
			throw new IllegalArgumentException(name + " is for the system only, so it cannot be offered to the user role or "
					+ "used before the email address is verified");
		}
	}

	/** An action decided without a record. Granted through roles until {@link #forEveryUser} says otherwise. */
	public static Permission onCollection(String name, String description) {
		return new Permission(name, description, null, null, false, false, false);
	}

	/** An action on one record of the type. Granted through roles until {@link #forEveryUser} says otherwise. */
	public static Permission onRecord(String name, String resourceType, String description) {
		return new Permission(name, description, Objects.requireNonNull(resourceType, "resourceType"), null, false, false,
				false);
	}

	/** The built-in {@code user} role is offered the action for every record (or for the collection). */
	public Permission forEveryUser() {
		return forEveryUser(Reach.ALL);
	}

	/** The built-in {@code user} role is offered the action with this reach, for example {@link Reach#owner()}. */
	public Permission forEveryUser(Reach reach) {
		return new Permission(this.name, this.description, this.resourceType, Objects.requireNonNull(reach, "reach"),
				this.allowedBeforeVerification, this.sensitive, this.systemOnly);
	}

	/** The same action, usable by a caller who has not verified their email address yet. */
	public Permission beforeVerification() {
		return new Permission(this.name, this.description, this.resourceType, this.userRoleReach, true, this.sensitive,
				this.systemOnly);
	}

	/**
	 * The same action, with its allowed uses recorded as well as its refusals. For actions whose every use should be
	 * reviewable afterwards: erasing, purging, granting.
	 */
	public Permission asSensitive() {
		return new Permission(this.name, this.description, this.resourceType, this.userRoleReach,
				this.allowedBeforeVerification, true, this.systemOnly);
	}

	/**
	 * The same action, for the system only. For what a job does on its own, such as a retention purge: the code still checks
	 * it, so it shows in the declarations and in the decision record, but nobody can be given it (see {@code systemOnly}).
	 */
	public Permission forSystemOnly() {
		return new Permission(this.name, this.description, this.resourceType, this.userRoleReach,
				this.allowedBeforeVerification, this.sensitive, true);
	}

	/** Whether a role can be given the action. False for an action only the system performs. */
	public boolean isGrantable() {
		return !this.systemOnly;
	}

	public boolean isOnRecord() {
		return this.resourceType != null;
	}

	public boolean grantedToUserRole() {
		return this.userRoleReach != null;
	}

}
