package com.kibootia.backend.policy.opa;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.kibootia.backend.platform.authorization.Principal;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.authorization.ResourceRule;

/**
 * An Open Policy Agent policy as a guardrail (ADR 0066). For the configured actions, after Kibootia's grants have allowed,
 * OPA is asked for the set of reasons to refuse; any reason refuses. It is a {@link ResourceRule}, so the combination rule
 * of the decision point holds: OPA can only narrow what roles allow, never widen it, and it is not asked at all where
 * grants already refused or for the system.
 *
 * <p>It fails closed. An unreachable or slow OPA, an error answer, or a policy path that is not defined throws, which the
 * decision point turns into a refusal with reason {@code authorization.evaluation_error}, logged as an error. Only the
 * configured actions are affected, so an outage cannot stop the rest of the application.
 *
 * <p>The request is {@code POST /v1/data/<path>} with this input, identifiers only:
 *
 * <pre>{@code
 * {"input": {"action": "identity.users.erase",
 *            "principal": {"kind": "user", "id": "...", "tenant": "..."},
 *            "resource": null | {"type": "notes.note", "id": "...", "tenant": "...", "relations": {"owner": ["..."]}}}}
 * }</pre>
 *
 * and the answer must be {@code {"result": [reason, ...]}}, a set of strings, empty for no objection.
 */
public class OpaGuardrail implements ResourceRule {

	private final OpaProperties properties;

	private final List<Pattern> actions;

	private final RestClient http;

	public OpaGuardrail(OpaProperties properties) {
		properties.requireComplete();
		this.properties = properties;
		this.actions = properties.actions().stream().map(OpaGuardrail::glob).toList();
		HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
		requests.setReadTimeout(properties.readTimeout());
		this.http = RestClient.builder().baseUrl(properties.url().toString()).requestFactory(requests).build();
	}

	private static Pattern glob(String pattern) {
		StringBuilder regex = new StringBuilder();
		for (char c : pattern.toCharArray()) {
			regex.append((c == '*') ? ".*" : Pattern.quote(String.valueOf(c)));
		}
		return Pattern.compile(regex.toString());
	}

	@Override
	public String name() {
		return "opa:" + this.properties.path();
	}

	/** Every record type, and the collection, of the configured actions. */
	@Override
	public boolean appliesTo(String resourceType, String action) {
		return this.actions.stream().anyMatch((pattern) -> pattern.matcher(action).matches());
	}

	@Override
	public Optional<String> objection(Principal principal, String action, ResourceRef resource) {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("action", action);
		input.put("principal",
				Map.of("kind", principal.kind(), "id", principal.id().toString(), "tenant", principal.tenantId().toString()));
		input.put("resource", (resource == null) ? null : resource(resource));
		Map<?, ?> answer;
		try {
			answer = this.http.post()
				// Not a URI template variable: that would encode the slashes of the path. The path is validated to letters,
				// digits, underscores and slashes (OpaProperties), so it is safe to append.
				.uri("/v1/data/" + this.properties.path())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("input", input))
				.retrieve()
				.body(Map.class);
		}
		catch (RestClientException ex) {
			throw new IllegalStateException("OPA could not be asked about " + action + ": " + ex.getMessage(), ex);
		}
		if (answer == null || !answer.containsKey("result")) {
			// OPA answers {} for a path no policy defines: a configuration error, never a silent allow.
			throw new IllegalStateException("OPA has no policy at " + this.properties.path());
		}
		if (!(answer.get("result") instanceof List<?> reasons)) {
			throw new IllegalStateException("OPA's answer at " + this.properties.path() + " is not a set of reasons");
		}
		return reasons.isEmpty() ? Optional.empty()
				: Optional.of(String.join("; ", reasons.stream().map(String::valueOf).toList()));
	}

	private static Map<String, Object> resource(ResourceRef resource) {
		Map<String, Object> relations = new LinkedHashMap<>();
		resource.relations()
			.forEach((name, subjects) -> relations.put(name, subjects.stream().map(Object::toString).sorted().toList()));
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("type", resource.type());
		json.put("id", resource.id().toString());
		json.put("tenant", resource.tenantId().toString());
		json.put("relations", relations);
		return json;
	}

}
