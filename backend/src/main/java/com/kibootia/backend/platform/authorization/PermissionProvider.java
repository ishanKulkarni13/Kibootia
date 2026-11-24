package com.kibootia.backend.platform.authorization;

import java.util.List;

/**
 * Implemented by a module as a Spring bean to declare its actions (ADR 0044) and the resource types they act on (ADR
 * 0054). They are collected at startup into the registry the role editor lists from; a malformed or duplicate name, an
 * undeclared resource type or a reach a type cannot have stops the application from starting.
 */
public interface PermissionProvider {

	List<Permission> permissions();

	/** The resource types the module's actions on records refer to. */
	default List<ResourceType> resourceTypes() {
		return List.of();
	}

}
