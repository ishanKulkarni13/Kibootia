package com.kibootia.backend.access.web;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * An action a module declares, as the role editor lists it (ADR 0054). {@code reaches} are the reaches a grant of it can
 * have, widest first; an action on the collection has only {@code all}.
 */
public record PermissionResponse(
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) String description,
		@Schema(nullable = true, description = "The resource type of an action on a record; null for an action on the collection.") String resourceType,
		@Schema(requiredMode = REQUIRED) List<String> reaches,
		@Schema(nullable = true, description = "The reach the built-in user role is offered it with; null when it is not.") String userRoleReach,
		@Schema(requiredMode = REQUIRED, description = "Whether allowed uses are recorded as well as refusals.") boolean sensitive) {

}
