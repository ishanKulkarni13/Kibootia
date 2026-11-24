package com.kibootia.backend.platform.authorization;

/**
 * The one place that answers authorization questions (ADR 0027, contract version 2 of ADR 0055). The {@code access}
 * module implements it in Java; an external engine can replace it, which is why it is a port. Modules do not call it
 * directly; they use {@link Authorization}, which also records the decisions.
 *
 * <p>An implementation must follow the combination rule: default deny; a deny decides; a module rule
 * ({@link ResourceRule}) can only narrow; an error while evaluating a rule denies. An undeclared action is a
 * programming error and throws {@link IllegalStateException}.
 */
public interface PolicyDecisionPoint {

	/**
	 * May the principal perform the action?
	 *
	 * @param resource the record, or null for an action on the collection (create, list), which is decided from grants
	 *        alone
	 */
	Decision decide(Principal principal, String action, ResourceRef resource);

	/** Which records the principal may perform the action on, for a list. */
	AccessFilter filter(Principal principal, String action);

	/** Whether allowed decisions of this action are recorded too, not only refusals; declared by its module. */
	default boolean isSensitive(String action) {
		return false;
	}

}
