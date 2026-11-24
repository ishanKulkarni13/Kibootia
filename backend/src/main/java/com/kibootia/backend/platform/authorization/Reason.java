package com.kibootia.backend.platform.authorization;

/**
 * Why a decision came out the way it did (ADR 0055). The codes are part of the contract, like the problem codes of the
 * HTTP API: they appear in decision records, metrics and logs, and must stay stable. Add a code; never rename one.
 */
public enum Reason {

	/** A grant the principal holds covers the action and, for a record, reaches it. */
	GRANTED("authorization.granted", true),

	/** System authority, entered with a recorded reason. */
	SYSTEM("authorization.system", true),

	/** The principal holds no grant for the action. Decided without looking at any record. */
	NO_GRANT("authorization.no_grant", false),

	/** The principal holds the action, but no grant of theirs reaches this record (for example, not its owner). */
	OUT_OF_REACH("authorization.out_of_reach", false),

	/** The record belongs to another tenant. A second line behind the tenant filter on queries (ADR 0028). */
	OTHER_TENANT("authorization.other_tenant", false),

	/** The principal has not verified their email address, and the action is not open before verification (ADR 0041). */
	EMAIL_NOT_VERIFIED("authorization.email_not_verified", false),

	/** A grant allowed it, and a module rule narrowed it away. */
	REFUSED_BY_RULE("authorization.refused_by_rule", false),

	/** A deny grant applies. A deny decides over every allow (ADR 0054, rule 5). */
	DENIED_BY_GRANT("authorization.denied_by_grant", false),

	/** Evaluating a rule failed. Fails closed, and is logged as an error rather than as an ordinary refusal. */
	EVALUATION_ERROR("authorization.evaluation_error", false),

	/** No decision point exists (the {@code access} module is absent), so everything is denied. */
	NO_DECISION_POINT("authorization.no_decision_point", false);

	private final String code;

	private final boolean allows;

	Reason(String code, boolean allows) {
		this.code = code;
		this.allows = allows;
	}

	public String code() {
		return this.code;
	}

	/** Whether a decision with this reason allows; a decision cannot carry an allowing reason and deny. */
	public boolean allows() {
		return this.allows;
	}

}
