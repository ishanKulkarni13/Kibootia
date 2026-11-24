package com.kibootia.backend.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.test.context.TestPropertySource;

import com.kibootia.backend.AdminApiTest;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The guardrail fails closed (ADR 0066): with OPA unreachable the actions it is asked about are refused, and every other
 * action works as before, so an outage stops only what the policy guards.
 */
@TestPropertySource(properties = { "kibootia.policy.opa.enabled=true", "kibootia.policy.opa.url=http://127.0.0.1:9",
		"kibootia.policy.opa.actions=widgets.widgets.create", "kibootia.policy.opa.connect-timeout=PT0.2S" })
class OpaUnreachableIT extends AdminApiTest {

	@Test
	@DisplayName("an unreachable OPA refuses the guarded action and leaves the others alone")
	void failsClosed() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isForbidden());
		get(user(ALICE), "/api/v1/widgets").andExpect(status().isOk());
		get(user(ALICE), "/api/v1/me").andExpect(status().isOk());
	}

}
