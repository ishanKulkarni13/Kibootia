package com.kibootia.backend.platform.authorization;

import java.util.Objects;

/**
 * The answer to one authorization question (ADR 0055): allow or deny, a stable reason, and what decided it.
 *
 * @param reason why; it also says whether the decision allows ({@link Reason#allows()})
 * @param decidedBy the grant or rule that decided, for example {@code notes.notes.update-any} or the name of a module
 *        rule; empty when nothing in particular decided (no grant at all)
 */
public record Decision(Reason reason, String decidedBy) {

	public Decision {
		Objects.requireNonNull(reason, "reason");
		decidedBy = (decidedBy == null) ? "" : decidedBy;
	}

	public static Decision of(Reason reason, String decidedBy) {
		return new Decision(reason, decidedBy);
	}

	public static Decision of(Reason reason) {
		return new Decision(reason, "");
	}

	public boolean allowed() {
		return this.reason.allows();
	}

}
