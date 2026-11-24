package com.kibootia.backend.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.test.context.TestPropertySource;

import com.kibootia.backend.AdminApiTest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deny grants switched on (ADR 0054, rule 5): a deny decides over an allow from any other role. */
@TestPropertySource(properties = "kibootia.access.deny-grants-enabled=true")
class DenyGrantsIT extends AdminApiTest {

	@Test
	@DisplayName("a deny in one role beats an allow in another, at once, and taking the deny role away restores access")
	void denyBeatsAllow() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String userRole = builtInRoleId("USER");
		String viewers = createRole("Role viewers", "access.roles.view");
		String body = "{\"name\":\"No role viewing\",\"description\":\"\",\"grants\":"
				+ "[{\"action\":\"access.roles.view\",\"reach\":\"all\",\"effect\":\"DENY\"}]}";
		String deniers = read(post(admin(), "/api/v1/roles", body).andExpect(status().isCreated()), "$.id");
		get(admin(), "/api/v1/permissions").andExpect(jsonPath("$.denyGrantsEnabled").value(true));

		setRoles(ALICE, userRole, viewers);
		get(user(ALICE), "/api/v1/roles").andExpect(status().isOk());

		setRoles(ALICE, userRole, viewers, deniers);
		get(user(ALICE), "/api/v1/roles").andExpect(status().isForbidden());
		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.permissions", not(hasItem("access.roles.view"))));

		setRoles(ALICE, userRole, viewers);
		get(user(ALICE), "/api/v1/roles").andExpect(status().isOk());
	}

	@Test
	@DisplayName("a deny must reach every record: a deny of only one's own is refused")
	void partialDenyIsRefused() throws Exception {
		ensureAdmin();
		post(admin(), "/api/v1/roles", "{\"name\":\"Odd\",\"description\":\"\",\"grants\":"
				+ "[{\"action\":\"widgets.widgets.view\",\"reach\":\"relation:owner\",\"effect\":\"DENY\"}]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("invalid_reach"));
	}

}
