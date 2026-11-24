package com.kibootia.backend.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.AdminApiTest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roles as sets of grants, through the API (ADR 0054, milestone 8): the catalogue of actions and their reaches, the
 * reach-aware "only what you hold" rule, validation of reaches and effects, deny grants off by default, and the reaches
 * {@code /me} reports. Deny grants switched on are in {@link DenyGrantsIT}.
 */
class GrantsApiIT extends AdminApiTest {

	private static String role(String name, String grants) {
		return "{\"name\":\"" + name + "\",\"description\":\"\",\"grants\":" + grants + "}";
	}

	@Test
	@DisplayName("the catalogue lists each action with the reaches a grant of it can have, and whether deny grants are on")
	void catalogue() throws Exception {
		ensureAdmin();
		get(admin(), "/api/v1/permissions").andExpect(status().isOk())
			.andExpect(jsonPath("$.denyGrantsEnabled").value(false))
			.andExpect(jsonPath("$.permissions[?(@.name == 'widgets.widgets.view')].reaches[*]", contains("all", "relation:owner")))
			.andExpect(jsonPath("$.permissions[?(@.name == 'widgets.widgets.view')].resourceType", contains("widgets.widget")))
			.andExpect(jsonPath("$.permissions[?(@.name == 'widgets.widgets.view')].userRoleReach", contains("relation:owner")))
			.andExpect(jsonPath("$.permissions[?(@.name == 'widgets.widgets.restore')].reaches[*]", contains("all")))
			.andExpect(jsonPath("$.permissions[?(@.name == 'access.roles.assign')].sensitive", contains(true)));
	}

	@Test
	@DisplayName("an editor who holds an action only for their own records cannot grant it for everyone's, but can for one's own")
	void reachIsPartOfTheRule() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		// Alice manages roles, and holds widgets.widgets.view for her own widgets only, through the user role.
		setRoles(ALICE, builtInRoleId("USER"), createRole("Role managers", "access.roles.view", "access.roles.manage"));

		post(user(ALICE), "/api/v1/roles", role("Widget viewers", grants("widgets.widgets.view")))
			.andExpect(status().isForbidden());
		post(user(ALICE), "/api/v1/roles", role("Own widget viewers", grants("widgets.widgets.view/relation:owner")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.grants[0].reach").value("relation:owner"))
			.andExpect(jsonPath("$.grants[0].effect").value("ALLOW"));
		assertCount("select count(*) from role where name = 'Widget viewers'", 0);
	}

	@Test
	@DisplayName("an assigner cannot give a role reaching further than they do")
	void assignmentRespectsReach() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		String wide = createRole("Widget viewers", "widgets.widgets.view");
		String narrow = createRole("Own widget viewers", "widgets.widgets.view/relation:owner");
		setRoles(ALICE, builtInRoleId("USER"), createRole("Assigners", "access.roles.view", "access.roles.assign"));

		put(user(ALICE), "/api/v1/users/" + BOB + "/roles",
				"{\"roleIds\":[\"" + builtInRoleId("USER") + "\",\"" + wide + "\"]}")
			.andExpect(status().isForbidden());
		put(user(ALICE), "/api/v1/users/" + BOB + "/roles",
				"{\"roleIds\":[\"" + builtInRoleId("USER") + "\",\"" + narrow + "\"]}")
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("a reach the action cannot have, a malformed reach and an unknown action are 400 with a field error")
	void invalidGrantsAreRefused() throws Exception {
		ensureAdmin();
		post(admin(), "/api/v1/roles", role("A", grants("widgets.widgets.restore/relation:owner")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("invalid_reach"));
		post(admin(), "/api/v1/roles", role("B", grants("widgets.widgets.view/relation:reviewer")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("invalid_reach"));
		post(admin(), "/api/v1/roles", role("C", "[{\"action\":\"widgets.widgets.view\",\"reach\":\"everything\"}]"))
			.andExpect(status().isBadRequest());
		post(admin(), "/api/v1/roles", role("D", grants("widgets.widgets.typo")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_permission"));
	}

	@Test
	@DisplayName("deny grants are off by default: a role cannot be given one")
	void denyGrantsAreOffByDefault() throws Exception {
		ensureAdmin();
		post(admin(), "/api/v1/roles",
				role("Deniers", "[{\"action\":\"widgets.widgets.view\",\"reach\":\"all\",\"effect\":\"DENY\"}]"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("deny_grants_disabled"));
	}

	@Test
	@DisplayName("/me reports how far each held action reaches")
	void meReportsReaches() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		get(user(ALICE), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.reaches['widgets.widgets.view']", contains("relation:owner")))
			.andExpect(jsonPath("$.reaches['widgets.widgets.create']", contains("all")));
		setRoles(ALICE, builtInRoleId("USER"), createRole("Widget viewers", "widgets.widgets.view"));
		get(user(ALICE), "/api/v1/me")
			.andExpect(jsonPath("$.reaches['widgets.widgets.view']", contains("all")))
			.andExpect(jsonPath("$.reaches['widgets.widgets.view']", hasSize(1)));
	}

	private void assertCount(String sql, int expected) {
		org.assertj.core.api.Assertions.assertThat(count(sql)).isEqualTo(expected);
	}

}
