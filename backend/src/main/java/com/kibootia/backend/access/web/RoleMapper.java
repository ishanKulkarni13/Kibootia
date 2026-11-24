package com.kibootia.backend.access.web;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.kibootia.backend.access.RoleSummary;
import com.kibootia.backend.access.application.GrantableAction;
import com.kibootia.backend.access.application.NewRole;
import com.kibootia.backend.access.application.Role;
import com.kibootia.backend.access.application.RoleChanges;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;

/** Converts between API types and application types by hand (ADR 0005). */
final class RoleMapper {

	private RoleMapper() {
	}

	static NewRole toNewRole(CreateRoleRequest request) {
		return new NewRole(request.name(), request.description(), toGrants(request.grants()));
	}

	static RoleChanges toChanges(UpdateRoleRequest request) {
		return new RoleChanges(request.name(), request.description(), toGrants(request.grants()), request.version());
	}

	/**
	 * The grants of a request. Bean validation has checked the shape of each reach and effect; a reach that still does not
	 * parse is a 400 with a field error, never a server error.
	 */
	static Set<Grant> toGrants(Set<GrantBody> bodies) {
		Set<Grant> grants = new LinkedHashSet<>();
		List<ApiFieldError> errors = new ArrayList<>();
		for (GrantBody body : bodies) {
			try {
				Grant.Effect effect = (body.effect() == null) ? Grant.Effect.ALLOW : Grant.Effect.valueOf(body.effect());
				grants.add(new Grant(body.action(), Reach.parse(body.reach()), effect));
			}
			catch (IllegalArgumentException ex) {
				errors.add(new ApiFieldError("grants", "invalid_reach", ex.getMessage()));
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
		return grants;
	}

	static GrantBody toBody(Grant grant) {
		return new GrantBody(grant.action(), grant.reach().code(), grant.effect().name());
	}

	static RoleResponse toResponse(Role role) {
		return new RoleResponse(role.id(), role.name(), role.description(),
				(role.builtIn() != null) ? role.builtIn().name() : null,
				role.grants().stream().map(RoleMapper::toBody).toList(), role.assignedUsers(), role.version(),
				role.createdAt(), role.updatedAt());
	}

	static RoleSummaryResponse toResponse(RoleSummary role) {
		return new RoleSummaryResponse(role.id(), role.name(), role.builtIn());
	}

	static List<RoleSummaryResponse> toResponses(List<RoleSummary> roles) {
		return roles.stream().map(RoleMapper::toResponse).toList();
	}

	static PermissionResponse toResponse(GrantableAction action) {
		Permission permission = action.permission();
		return new PermissionResponse(permission.name(), permission.description(), permission.resourceType(),
				action.reaches().stream().map(Reach::code).toList(),
				(permission.userRoleReach() != null) ? permission.userRoleReach().code() : null, permission.sensitive());
	}

}
