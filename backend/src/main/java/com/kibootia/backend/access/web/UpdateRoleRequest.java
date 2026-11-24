package com.kibootia.backend.access.web;

import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/roles/{id}}. {@code version} is the version the client last read: if the role changed
 * since, the update is refused with 409 instead of overwriting the other change.
 */
public record UpdateRoleRequest(@NotBlank @Size(max = 80) String name, @NotNull @Size(max = 255) String description,
		@NotNull @Size(max = 500) Set<@NotNull @Valid GrantBody> grants, @NotNull @PositiveOrZero Long version) {

}
