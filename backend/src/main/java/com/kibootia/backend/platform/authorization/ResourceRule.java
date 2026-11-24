package com.kibootia.backend.platform.authorization;

import java.util.Optional;

/**
 * A module's own rule for records of one type, for what grants cannot say (a record that is locked, a state in which
 * nobody may change it). Implemented as a Spring bean (ADR 0055, point 3).
 *
 * <p>A rule can only <em>narrow</em>: it is consulted after a grant has allowed the action on the record, and it can
 * only object. It can never allow what grants do not, so the role editor and the audit trail still describe what a
 * person can do (finding S3 of the security review). Every applicable rule is consulted; any objection denies. A rule
 * that throws denies too, and the failure is logged as an error.
 *
 * <p>A rule applies to checks of one record. It does not narrow a list: the access filter of a list comes from grants
 * alone. A module whose rule must also hide records from lists applies the same condition in its query. Named conditions
 * with a database form, which cover both, arrive in milestone 11.
 */
public interface ResourceRule {

	/** A short stable name, written to decision records when the rule objects. */
	String name();

	/** Whether the rule has anything to say about this action on records of this type. */
	boolean appliesTo(String resourceType, String action);

	/**
	 * Why the action must not happen to this record, or empty when the rule has no objection.
	 *
	 * @return a short explanation for logs; it is not shown to the caller
	 */
	Optional<String> objection(Principal principal, String action, ResourceRef resource);

}
