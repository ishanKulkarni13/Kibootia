package com.kibootia.backend.platform.authorization;

import java.util.Objects;
import java.util.Set;

/**
 * A kind of record a module protects (ADR 0054), declared in code with the relations a principal can have to one record.
 * A relation it declares is a reach a grant of its actions can have: a type with {@code owner} can be granted for "my
 * own records". Undeclared types and relations stop the application at startup.
 *
 * @param name {@code <module>.<resource>}, for example {@code notes.note}
 * @param relations the relations a record of this type can carry, for example {@code owner}
 */
public record ResourceType(String name, String description, Set<String> relations) {

	public ResourceType {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		relations = Set.copyOf(relations);
	}

	public static ResourceType of(String name, String description, String... relations) {
		return new ResourceType(name, description, Set.of(relations));
	}

}
