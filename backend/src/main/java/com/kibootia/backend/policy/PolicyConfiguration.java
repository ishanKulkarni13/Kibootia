package com.kibootia.backend.policy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.kibootia.backend.platform.authorization.ResourceRule;
import com.kibootia.backend.policy.opa.OpaGuardrail;
import com.kibootia.backend.policy.opa.OpaProperties;

/**
 * Registers the Open Policy Agent guardrail as a narrowing rule of the decision point (ADR 0066), only while
 * {@code kibootia.policy.opa.enabled} is true. With it off the module adds nothing, and no setting of it is needed.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpaProperties.class)
class PolicyConfiguration {

	@Bean
	@ConditionalOnProperty(name = "kibootia.policy.opa.enabled", havingValue = "true")
	ResourceRule opaGuardrail(OpaProperties properties) {
		return new OpaGuardrail(properties);
	}

}
