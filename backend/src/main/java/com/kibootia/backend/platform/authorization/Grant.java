package com.kibootia.backend.platform.authorization;

import java.util.Objects;

/**
 * One entry of a role (ADR 0054): an action, how far it reaches, and whether it allows or denies. A grant is the only
 * thing that gives access; a deny grant takes it away again and decides over every allow (rule 5).
 *
 * @param action the declared action, {@code <module>.<resource>.<action>}
 */
public record Grant(String action, Reach reach, Effect effect) {

	public enum Effect {

		ALLOW, DENY

	}

	public Grant {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(reach, "reach");
		Objects.requireNonNull(effect, "effect");
	}

	public static Grant allow(String action, Reach reach) {
		return new Grant(action, reach, Effect.ALLOW);
	}

	public static Grant deny(String action) {
		return new Grant(action, Reach.ALL, Effect.DENY);
	}

	public boolean allows() {
		return this.effect == Effect.ALLOW;
	}

	/**
	 * A short form for audit entries and decision records: {@code notes.notes.update/relation:owner}, with {@code !} for
	 * a deny. No {@code @}: the audit log refuses values that look like an email address (ADR 0046).
	 */
	public String describe() {
		return (allows() ? "" : "!") + this.action + "/" + this.reach.code();
	}

}
