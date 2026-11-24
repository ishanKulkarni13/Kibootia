package com.kibootia.backend.access.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * One grant of a role as the API sends and receives it (ADR 0054): an action, how far it reaches ({@code all}, or
 * {@code relation:owner} for the records the holder owns), and whether it allows or denies. Omitting {@code effect}
 * means {@code ALLOW}.
 */
public record GrantBody(
		@Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 100) String action,
		@Schema(requiredMode = REQUIRED, example = "relation:owner") @NotBlank @Size(max = 100)
		@Pattern(regexp = "all|relation:[a-z][a-z0-9-]*") String reach,
		@Schema(allowableValues = { "ALLOW", "DENY" }, defaultValue = "ALLOW") @Pattern(regexp = "ALLOW|DENY") String effect) {

}
