package com.kibootia.backend.policy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.kibootia.backend.AdminApiTest;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Open Policy Agent guardrail against a real OPA (ADR 0066): it narrows what grants allow for the configured
 * actions, its policy can change while the application runs, and the actions it is not asked about are untouched. That
 * it can never widen access is the combination rule, shown in {@code DefaultDecisionPointTest}.
 */
class OpaGuardrailIT extends AdminApiTest {

	/** The version pinned in compose.yaml. */
	static final GenericContainer<?> OPA = new GenericContainer<>("openpolicyagent/opa:1.22.2")
		.withCopyFileToContainer(MountableFile.forClasspathResource("opa/guardrails.rego"), "/policies/guardrails.rego")
		.withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies")
		.withExposedPorts(8181)
		.waitingFor(Wait.forHttp("/health").forPort(8181));

	static {
		OPA.start();
	}

	@DynamicPropertySource
	static void opa(DynamicPropertyRegistry registry) {
		registry.add("kibootia.policy.opa.enabled", () -> "true");
		registry.add("kibootia.policy.opa.url", () -> "http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181));
		registry.add("kibootia.policy.opa.actions", () -> "widgets.widgets.create");
	}

	private static void putData(String path, String json) throws Exception {
		HttpRequest request = HttpRequest
			.newBuilder(URI.create("http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181) + "/v1/data/" + path))
			.header("Content-Type", "application/json")
			.PUT(HttpRequest.BodyPublishers.ofString(json))
			.build();
		int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
		if (status != 204) {
			throw new IllegalStateException("OPA refused the data: " + status);
		}
	}

	@AfterEach
	void thaw() throws Exception {
		putData("frozen", "false");
		putData("blocked", "[]");
	}

	@Test
	@DisplayName("a policy with no objection lets grants decide; once it objects, the action is refused, and it can change at run time")
	void narrowsAtRunTime() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isCreated());

		putData("frozen", "true");
		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isForbidden());
		// Administrators too: a guardrail sits above roles.
		post(admin(), "/api/v1/widgets", "").andExpect(status().isForbidden());

		putData("frozen", "false");
		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("only the configured actions are asked about: listing widgets ignores a block that refuses creating them")
	void onlyConfiguredActions() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		putData("blocked", "[\"" + ALICE + "\"]");
		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isForbidden());
		get(user(ALICE), "/api/v1/widgets").andExpect(status().isOk());
		post(user(BOB), "/api/v1/widgets", "").andExpect(status().isCreated());
	}

}
