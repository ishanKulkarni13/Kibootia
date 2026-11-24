/**
 * External policy module (ADR 0066): guardrails kept outside the application, such as an Open Policy Agent policy, that
 * can only narrow what Kibootia's grants allow. Optional and off by default ({@code kibootia.policy.opa.enabled}); a fork
 * that does not want it deletes the package. See kibootia-docs/recipes/enable-opa-guardrails.md.
 */
@ApplicationModule(displayName = "External policy", allowedDependencies = { "platform :: *" })
package com.kibootia.backend.policy;

import org.springframework.modulith.ApplicationModule;
