package com.kibootia.backend.access.application;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What starting the application does to the built-in roles (ADR 0041): it offers the user role each permission a module
 * flags for it, once, so an administrator's removals are still there after the next start.
 */
class BuiltInRolesIT extends AdminApiTest {

	@Autowired
	BuiltInRoles builtInRoles;

	@Test
	@DisplayName("a permission an administrator took off the user role stays off after a restart, a newly flagged one is offered once")
	void removalsSurviveAndNewPermissionsAreOffered() throws Exception {
		ensureAdmin();
		String userRole = builtInRoleId("USER");
		String current = get(admin(), "/api/v1/roles/" + userRole).andReturn().getResponse().getContentAsString();
		long version = ((Number) JsonPath.read(current, "$.version")).longValue();
		put(admin(), "/api/v1/roles/" + userRole, "{\"name\":\"User\",\"description\":\"x\",\"grants\":"
				+ "[{\"action\":\"widgets.widgets.view\",\"reach\":\"relation:owner\"},{\"action\":\"widgets.widgets.update\",\"reach\":\"relation:owner\"},{\"action\":\"widgets.widgets.delete\",\"reach\":\"relation:owner\"}],\"version\":" + version + "}")
			.andExpect(status().isOk());

		// A permission the user role was never offered, as when a module adds one in a new release.
		this.jdbc.update("delete from role_permission_seed where permission = 'widgets.widgets.update'");
		this.jdbc.update("delete from role_grant where action = 'widgets.widgets.update'");

		this.builtInRoles.run(null);

		get(admin(), "/api/v1/roles/" + userRole).andExpect(status().isOk())
			.andExpect(jsonPath("$.grants[*].action", hasItem("widgets.widgets.update")))
			.andExpect(jsonPath("$.grants[*].action", not(hasItem("widgets.widgets.create"))));

		this.builtInRoles.run(null);
		get(admin(), "/api/v1/roles/" + userRole).andExpect(jsonPath("$.grants[*].action", not(hasItem("widgets.widgets.create"))));
	}

	@Test
	@DisplayName("the administrator role holds every declared action for every record, and a missing grant is back after a start")
	void administratorGrantsAreKeptComplete() throws Exception {
		ensureAdmin();
		String adminRole = builtInRoleId("ADMIN");
		// As when a module adds an action in a new release: the administrator has no grant for it yet.
		this.jdbc.update("delete from role_grant where role_id = unhex(?) and action = 'widgets.widgets.restore'",
				adminRole.replace("-", ""));
		get(admin(), "/api/v1/roles/" + adminRole)
			.andExpect(jsonPath("$.grants[*].action", not(hasItem("widgets.widgets.restore"))));

		this.builtInRoles.run(null);
		get(admin(), "/api/v1/roles/" + adminRole)
			.andExpect(jsonPath("$.grants[?(@.action == 'widgets.widgets.restore')].reach", contains("all")))
			.andExpect(jsonPath("$.grants[?(@.reach != 'all')]", hasSize(0)))
			.andExpect(jsonPath("$.grants[?(@.effect != 'ALLOW')]", hasSize(0)));
		int declared = JsonPath.read(get(admin(), "/api/v1/permissions").andReturn().getResponse().getContentAsString(),
				"$.permissions.length()");
		assertThat(count("select count(*) from role_grant where role_id = unhex(?)", adminRole.replace("-", "")))
			.isEqualTo(declared);
	}

	@Test
	@DisplayName("permissions stored by contract version 1 become grants at startup; a name no module declares is left for later")
	void legacyPermissionsAreConverted() throws Exception {
		ensureAdmin();
		String role = createRole("Converted");
		String hex = role.replace("-", "");
		for (String legacy : List.of("widgets.widgets.view-any", "widgets.widgets.update", "widgets.widgets.restore",
				"gone.things.read")) {
			this.jdbc.update("insert into role_permission (tenant_id, role_id, permission) values (unhex(?), unhex(?), ?)",
					hex(TenantContext.DEFAULT_TENANT), hex, legacy);
		}

		this.builtInRoles.run(null);

		get(admin(), "/api/v1/roles/" + role)
			.andExpect(jsonPath("$.grants[?(@.action == 'widgets.widgets.view')].reach", contains("all")))
			.andExpect(jsonPath("$.grants[?(@.action == 'widgets.widgets.update')].reach", contains("relation:owner")))
			.andExpect(jsonPath("$.grants[?(@.action == 'widgets.widgets.restore')].reach", contains("all")))
			.andExpect(jsonPath("$.grants", hasSize(3)));
		assertThat(this.jdbc.queryForList("select permission from role_permission where role_id = unhex(?)", String.class,
				hex)).containsExactly("gone.things.read");

		// Running again converts nothing twice.
		this.builtInRoles.run(null);
		get(admin(), "/api/v1/roles/" + role).andExpect(jsonPath("$.grants", hasSize(3)));
	}

	@Test
	@DisplayName("the built-in roles are created again when they are missing, so a sign-in never fails for want of them")
	void missingBuiltInRolesAreRecreated() throws Exception {
		ensureAdmin();
		this.jdbc.update("delete from role_assignment");
		this.jdbc.update("delete from role_permission");
		this.jdbc.update("delete from role_permission_seed");
		this.jdbc.update("delete from role");
		this.jdbc.update("delete from user_profile");

		get(user(ALICE), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions", hasItem("widgets.widgets.create")));
	}

}
