package com.kibootia.backend.policy.opa;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the Open Policy Agent guardrail (ADR 0066).
 *
 * @param enabled whether the guardrail is consulted at all; off by default
 * @param url where OPA's REST API is reached, for example {@code http://opa:8181}
 * @param path the policy rule whose value is the set of reasons to refuse, as a path under {@code /v1/data}; an empty set
 *        means no objection
 * @param actions the actions OPA is asked about, each an action name or a pattern with {@code *} for any characters (for
 *        example {@code identity.users.*} or {@code *.purge}). Every other decision is made without calling OPA, so
 *        neither its latency nor an outage affects it
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for an answer; a slow OPA refuses rather than holding the request
 */
@ConfigurationProperties("kibootia.policy.opa")
@Validated
public record OpaProperties(@DefaultValue("false") boolean enabled, URI url,
		@DefaultValue("kibootia/guardrails/deny") @Pattern(regexp = "[A-Za-z0-9_]+(/[A-Za-z0-9_]+)*") String path,
		List<@Pattern(regexp = "[a-z0-9.*-]+") String> actions,
		@DefaultValue("PT0.5S") @NotNull Duration connectTimeout, @DefaultValue("PT1S") @NotNull Duration readTimeout) {

	public OpaProperties {
		actions = (actions == null) ? List.of() : List.copyOf(actions);
	}

	/** The settings that must be present while the guardrail is on; checked when it starts. */
	void requireComplete() {
		if (this.url == null) {
			throw new IllegalStateException("kibootia.policy.opa.url is required while kibootia.policy.opa.enabled is true");
		}
		if (this.actions.isEmpty()) {
			throw new IllegalStateException("kibootia.policy.opa.actions lists no action: the guardrail would never be "
					+ "asked. Name the actions OPA should decide on, or switch it off");
		}
	}

}
