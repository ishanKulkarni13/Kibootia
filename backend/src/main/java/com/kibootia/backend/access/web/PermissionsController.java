package com.kibootia.backend.access.web;

import java.util.Comparator;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.access.application.RoleService;

/**
 * Every action the modules declare in code, with the reaches a grant of it can have, for the role editor (ADR 0044,
 * ADR 0054). The list is short and fixed for a running application, so it is not paged.
 */
@RestController
@RequestMapping("/api/v1/permissions")
class PermissionsController {

	private final RoleService roles;

	PermissionsController(RoleService roles) {
		this.roles = roles;
	}

	@GetMapping
	PermissionCatalogueResponse list() {
		return new PermissionCatalogueResponse(this.roles.permissions()
			.stream()
			.sorted(Comparator.comparing((action) -> action.permission().name()))
			.map(RoleMapper::toResponse)
			.toList(), this.roles.denyGrantsEnabled());
	}

}
