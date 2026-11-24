package com.kibootia.backend.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.kibootia.backend.platform.authorization.ResourceRule;

import static org.assertj.core.api.Assertions.assertThat;

/** The guardrail is absent unless switched on, and refuses to start with settings that would make it useless. */
class PolicyConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(PolicyConfiguration.class);

	@Test
	@DisplayName("adds nothing while it is off, which is the default")
	void offByDefault() {
		this.runner.run((context) -> assertThat(context).hasNotFailed().doesNotHaveBean(ResourceRule.class));
	}

	@Test
	@DisplayName("registers the guardrail as a narrowing rule when it is on")
	void onRegistersTheRule() {
		this.runner
			.withPropertyValues("kibootia.policy.opa.enabled=true", "kibootia.policy.opa.url=http://opa:8181",
					"kibootia.policy.opa.actions=identity.users.*,*.purge")
			.run((context) -> {
				assertThat(context).hasSingleBean(ResourceRule.class);
				ResourceRule rule = context.getBean(ResourceRule.class);
				assertThat(rule.name()).isEqualTo("opa:kibootia/guardrails/deny");
				assertThat(rule.appliesTo(null, "identity.users.erase")).isTrue();
				assertThat(rule.appliesTo("widgets.widget", "widgets.widgets.purge")).isTrue();
				assertThat(rule.appliesTo(null, "widgets.widgets.view")).isFalse();
				assertThat(rule.appliesTo(null, "identityXusers.erase")).as("a dot is not a wildcard").isFalse();
			});
	}

	@Test
	@DisplayName("refuses to start on without an address or without any action to ask about")
	void refusesIncompleteSettings() {
		this.runner.withPropertyValues("kibootia.policy.opa.enabled=true", "kibootia.policy.opa.actions=x.y.z")
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.hasStackTraceContaining("kibootia.policy.opa.url"));
		this.runner.withPropertyValues("kibootia.policy.opa.enabled=true", "kibootia.policy.opa.url=http://opa:8181")
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.hasStackTraceContaining("kibootia.policy.opa.actions"));
	}

}
