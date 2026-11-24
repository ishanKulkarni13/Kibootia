package com.kibootia.backend.platform.authorization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What the decision point knows about one record (ADR 0055): its declared type, its ID, its tenant, and the principals
 * that stand in a named relation to it, such as its {@code owner}. The module that owns the record builds it, so the
 * decision point never reads another module's tables.
 *
 * <p>It carries identifiers only, never attribute values, so it can be written to a decision record as it is.
 *
 * @param type the resource type, {@code <module>.<resource>}, for example {@code notes.note}
 * @param relations for each relation name, the principals that have it; never null
 */
public record ResourceRef(String type, UUID id, UUID tenantId, Map<String, Set<UUID>> relations) {

	/** The relation every owned record declares (ADR 0054, rule 4). */
	public static final String OWNER = "owner";

	public ResourceRef {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(tenantId, "tenantId");
		Map<String, Set<UUID>> copy = new LinkedHashMap<>();
		if (relations != null) {
			relations.forEach((name, subjects) -> copy.put(name, Set.copyOf(subjects)));
		}
		relations = Collections.unmodifiableMap(copy);
	}

	public static ResourceRef of(String type, UUID id, UUID tenantId) {
		return new ResourceRef(type, id, tenantId, Map.of());
	}

	/** The same reference with one more principal in the relation; a null subject adds nothing. */
	public ResourceRef withRelation(String relation, UUID subject) {
		if (subject == null) {
			return this;
		}
		Map<String, Set<UUID>> next = new LinkedHashMap<>(this.relations);
		Set<UUID> subjects = new LinkedHashSet<>(next.getOrDefault(relation, Set.of()));
		subjects.add(subject);
		next.put(relation, subjects);
		return new ResourceRef(this.type, this.id, this.tenantId, next);
	}

	/** The same reference with the record's owner. */
	public ResourceRef ownedBy(UUID owner) {
		return withRelation(OWNER, owner);
	}

	public boolean hasRelation(String relation, UUID subject) {
		return this.relations.getOrDefault(relation, Set.of()).contains(subject);
	}

	@Override
	public String toString() {
		return this.type + ":" + this.id;
	}

}
