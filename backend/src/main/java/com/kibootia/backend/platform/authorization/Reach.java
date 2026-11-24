package com.kibootia.backend.platform.authorization;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How far a grant reaches (ADR 0054): every record of the tenant, or only the records to which the principal has a named
 * relation. It replaces the {@code -any} suffix of contract version 1: {@code notes.notes.update} with reach
 * {@code relation:owner} is "my own notes", with reach {@code all} it is "anyone's".
 *
 * <p>Stored and sent as its {@link #code()}: {@code all} or {@code relation:<name>}. Scope nodes are added as a further
 * kind in milestone 9.
 */
public sealed interface Reach permits Reach.All, Reach.Relation {

	All ALL = new All();

	/** Every record of the tenant. The only reach of an action on the collection. */
	record All() implements Reach {

		@Override
		public String code() {
			return "all";
		}

	}

	/** The records to which the principal has the relation, for example the ones they own. */
	record Relation(String name) implements Reach {

		private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");

		public Relation {
			Objects.requireNonNull(name, "name");
			if (!NAME.matcher(name).matches()) {
				throw new IllegalArgumentException("'" + name + "' is not a relation name");
			}
		}

		@Override
		public String code() {
			return "relation:" + this.name;
		}

	}

	static Reach relation(String name) {
		return new Relation(name);
	}

	/** The reach of the records a principal owns. */
	static Reach owner() {
		return new Relation(ResourceRef.OWNER);
	}

	String code();

	/** Whether a grant with this reach allows at least what a grant with {@code other} allows. */
	default boolean covers(Reach other) {
		return (this instanceof All) || this.equals(other);
	}

	/** The reach a {@link #code()} names; refuses anything else. */
	static Reach parse(String code) {
		if ("all".equals(code)) {
			return ALL;
		}
		if (code != null && code.startsWith("relation:")) {
			return new Relation(code.substring("relation:".length()));
		}
		throw new IllegalArgumentException("'" + code + "' is not a reach");
	}

}
