package com.kibootia.backend.platform.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which records of a type a principal may act on, for a list (ADR 0055, point 4). The module's store turns it into its
 * query condition, so the database filters and paging and totals stay correct (ADR 0044).
 *
 * <p>It replaces the three-value {@code Scope} of contract version 1. Today a filter is nothing, everything in the
 * tenant, or records to which the principal has a relation (their own records, by the relation {@code owner}). Scope
 * nodes and named conditions are added as further {@link Alternative} kinds in later phases. A store that meets a kind it
 * does not know must refuse, never ignore it: {@link #unsupported} says so.
 */
public sealed interface AccessFilter permits AccessFilter.Nothing, AccessFilter.Everything, AccessFilter.AnyOf {

	Nothing NOTHING = new Nothing();

	Everything EVERYTHING = new Everything();

	/** The principal may act on no record: the list is refused, or is empty. */
	record Nothing() implements AccessFilter {
	}

	/** Every record of the principal's tenant. */
	record Everything() implements AccessFilter {
	}

	/** Records that match at least one alternative. */
	record AnyOf(List<Alternative> alternatives) implements AccessFilter {

		public AnyOf {
			if (alternatives == null || alternatives.isEmpty()) {
				throw new IllegalArgumentException("An AnyOf filter needs at least one alternative; use NOTHING instead");
			}
			alternatives = List.copyOf(alternatives);
		}

	}

	/** One way a record can be reachable. */
	sealed interface Alternative permits RelatedTo {
	}

	/** Records to which {@code subject} has {@code relation}, for example the records the subject owns. */
	record RelatedTo(String relation, UUID subject) implements Alternative {
	}

	static AccessFilter relatedTo(String relation, UUID subject) {
		return new AnyOf(List.of(new RelatedTo(relation, subject)));
	}

	default boolean isNothing() {
		return this instanceof Nothing;
	}

	default boolean isEverything() {
		return this instanceof Everything;
	}

	/**
	 * For a store that only understands ownership: the one subject whose own records are allowed, or empty when every
	 * record is allowed. Any other shape is refused with {@link #unsupported}, so a store never widens a filter it does
	 * not understand. Call it only after {@link #isNothing} was ruled out.
	 */
	default Optional<UUID> ownerOnly() {
		return switch (this) {
			case Everything everything -> Optional.empty();
			case AnyOf(List<Alternative> alternatives) when alternatives.size() == 1
					&& alternatives.getFirst() instanceof RelatedTo(String relation, UUID subject)
					&& ResourceRef.OWNER.equals(relation) -> Optional.of(subject);
			default -> throw unsupported(this);
		};
	}

	static IllegalStateException unsupported(AccessFilter filter) {
		return new IllegalStateException("This store cannot apply the access filter " + filter
				+ "; refusing rather than listing too much");
	}

}
