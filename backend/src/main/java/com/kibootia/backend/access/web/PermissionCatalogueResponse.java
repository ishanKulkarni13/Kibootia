package com.kibootia.backend.access.web;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** What the role editor can offer: every declared action, and whether deny grants may be added. */
public record PermissionCatalogueResponse(
		@Schema(requiredMode = REQUIRED) List<PermissionResponse> permissions,
		@Schema(requiredMode = REQUIRED, description = "Whether roles may be given new deny grants (kibootia.access.deny-grants-enabled).") boolean denyGrantsEnabled) {

}
