package com.kibootia.backend.platform.authorization;

import java.util.Optional;

/**
 * A rule for what grants cannot say: about records of one type (a record that is locked, a state in which nobody may
 * change it), or about an action as a whole (a change freeze, a policy kept outside the application). Implemented as a
 * Spring bean, by a module or by an external policy adapter (ADR 0055, point 3; ADR 0066).
 *
 * <p>A rule can only <em>narrow</em>: it is consulted after a grant has allowed the action on the record, and it can
 * only object. It can never allow what grants do not, so the role editor and the audit trail still describe what a
 * person can do (finding S3 of the security review). Every applicable rule is consulted; any objection denies. A rule
 * that throws denies too, and the failure is logged as an error.
 *
 * <p>A rule is consulted for a check of one record, with that record, and for a check of the action on the collection,
 * without one ({@code resourceType} and {@code resource} are null). A list is refused entirely when a rule objects to
 * the action without a record; a rule cannot hide some records of a list, because the access filter comes from grants
 * alone. A module whose rule must also hide records from lists applies the same condition in its query. Named
 * conditions with a database form, which cover both, arrive in milestone 11.
 */
public interface ResourceRule {

	/** A short stable name, written to decision records when the rule objects. */
	String name();

	/**
	 * Whether the rule has anything to say about this action on records of this type.
	 *
	 * @param resourceType the type of the record, or null for the action on the collection
	 */
	boolean appliesTo(String resourceType, String action);

	/**
	 * Why the action must not happen to this record, or empty when the rule has no objection.
	 *
	 * @param resource the record, or null for the action on the collection
	 * @return a short explanation for logs; it is not shown to the caller
	 */
	Optional<String> objection(Principal principal, String action, ResourceRef resource);

}
