package com.kibootia.backend.policy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * The policy that ships in {@code infra/opa} (ADR 0066), with its data file, against a real OPA and the real guardrail.
 * {@link OpaGuardrailIT} runs a policy written for the tests; this is the one a fork starts from, so what its two rules
 * promise is shown here as well: a change freeze for named actions and a blocklist for named principals, both driven by
 * data that can change while OPA runs.
 */
class ShippedGuardrailPolicyIT extends AdminApiTest {

	/** The folder the test is run from is {@code backend/}; the policy is beside it in the repository. */
	private static final Path SHIPPED = Path.of("").toAbsolutePath().resolve("../infra/opa").normalize();

	/** The version pinned in compose.yaml. */
	static final GenericContainer<?> OPA = shippedOpa();

	private static GenericContainer<?> shippedOpa() {
		Path policy = SHIPPED.resolve("guardrails.rego");
		Path data = SHIPPED.resolve("data.json");
		if (!Files.isRegularFile(policy) || !Files.isRegularFile(data)) {
			throw new IllegalStateException("The shipped policy is not where this test expects it: " + SHIPPED
					+ ". Run the tests from a checkout of the whole repository.");
		}
		GenericContainer<?> opa = new GenericContainer<>("openpolicyagent/opa:1.22.2")
			.withCopyFileToContainer(MountableFile.forHostPath(policy), "/policies/guardrails.rego")
			.withCopyFileToContainer(MountableFile.forHostPath(data), "/policies/data.json")
			.withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies")
			.withExposedPorts(8181)
			.waitingFor(Wait.forHttp("/health").forPort(8181));
		opa.start();
		return opa;
	}

	@DynamicPropertySource
	static void opa(DynamicPropertyRegistry registry) {
		registry.add("kibootia.policy.opa.enabled", () -> "true");
		registry.add("kibootia.policy.opa.url", () -> "http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181));
		registry.add("kibootia.policy.opa.actions", () -> "widgets.widgets.create");
	}

	private static void putData(String path, String json) throws Exception {
		HttpRequest request = HttpRequest
			.newBuilder(URI.create("http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181) + "/v1/data/guardrails_config/" + path))
			.header("Content-Type", "application/json")
			.PUT(HttpRequest.BodyPublishers.ofString(json))
			.build();
		int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
		if (status != 204) {
			throw new IllegalStateException("OPA refused the data: " + status);
		}
	}

	@AfterEach
	void reset() throws Exception {
		putData("frozen_actions", "[]");
		putData("blocked_principals", "[]");
	}

	@Test
	@DisplayName("as shipped, with nothing listed in its data, the policy objects to nothing")
	void objectsToNothingAsShipped() throws Exception {
		ensureAdmin();
		signIn(ALICE);

		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isCreated());
		post(admin(), "/api/v1/widgets", "").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("a frozen action is refused to everyone, administrators too, for as long as it is listed")
	void aFrozenActionIsRefusedToEveryone() throws Exception {
		ensureAdmin();
		signIn(ALICE);

		putData("frozen_actions", "[\"widgets.widgets.create\"]");
		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isForbidden());
		post(admin(), "/api/v1/widgets", "").andExpect(status().isForbidden());

		putData("frozen_actions", "[]");
		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("a blocked principal is refused, and another is not")
	void aBlockedPrincipalIsRefused() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);

		putData("blocked_principals", "[\"" + ALICE + "\"]");
		post(user(ALICE), "/api/v1/widgets", "").andExpect(status().isForbidden());
		post(user(BOB), "/api/v1/widgets", "").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("only the configured actions are asked about: a frozen action that is not configured is not refused by OPA")
	void anActionNobodyAsksAboutIsUntouched() throws Exception {
		ensureAdmin();
		signIn(ALICE);

		putData("frozen_actions", "[\"widgets.widgets.view\"]");

		// Listing widgets is not among the actions Kibootia asks OPA about, so the freeze on it is never seen.
		get(user(ALICE), "/api/v1/widgets").andExpect(status().isOk());
	}

}
