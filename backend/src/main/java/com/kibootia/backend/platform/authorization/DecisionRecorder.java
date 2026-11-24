package com.kibootia.backend.platform.authorization;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Makes decisions observable (ADR 0055, point 6). Every refusal, and every allow of an action its module marks as
 * sensitive, becomes a <em>decision record</em>: one structured log line on the logger {@value #LOGGER} with the
 * principal, the action, the resource reference, the outcome, the reason code, what decided, and for the system the
 * reason it was entered. The trace ID comes with the line from the logging setup. Records hold identifiers only, never
 * attribute values (OQ-67).
 *
 * <p>Every decision, recorded or not, is also counted in the metric {@value #METRIC}, tagged with outcome, reason and
 * action. The tags have bounded values: reasons are an enum and actions are declared in code.
 *
 * <p>Where decision records are stored beyond the log, and for how long, is the decision log of milestone 15. They are
 * not written to the audit table, which is transactional and records changes, not reads (ADR 0046).
 */
@Component
class DecisionRecorder {

	static final String LOGGER = "kibootia.authorization.decision";

	static final String METRIC = "kibootia.authorization.decisions";

	private static final Logger log = LoggerFactory.getLogger(LOGGER);

	private final ObjectProvider<MeterRegistry> meters;

	DecisionRecorder(ObjectProvider<MeterRegistry> meters) {
		this.meters = meters;
	}

	void record(Principal principal, String action, ResourceRef resource, Decision decision, boolean sensitive) {
		count(action, decision);
		if (decision.allowed() && !sensitive) {
			return;
		}
		// An evaluation error is a defect to fix, not a refusal to read later, so it is an error.
		LoggingEventBuilder event = (decision.reason() == Reason.EVALUATION_ERROR) ? log.atError() : log.atInfo();
		event = event.addKeyValue("authorization.outcome", decision.allowed() ? "allow" : "deny")
			.addKeyValue("authorization.reason", decision.reason().code())
			.addKeyValue("authorization.action", action)
			.addKeyValue("authorization.principal.kind", principal.kind())
			.addKeyValue("authorization.principal.id", principal.id().toString())
			.addKeyValue("authorization.tenant", principal.tenantId().toString());
		if (!decision.decidedBy().isEmpty()) {
			event = event.addKeyValue("authorization.decided_by", decision.decidedBy());
		}
		if (resource != null) {
			event = event.addKeyValue("authorization.resource.type", resource.type())
				.addKeyValue("authorization.resource.id", resource.id().toString());
		}
		if (principal instanceof Principal.System system) {
			event = event.addKeyValue("authorization.system_reason", system.reason());
		}
		event.log("{} {} {}{}", decision.allowed() ? "Allowed" : "Denied", action,
				(resource != null) ? resource + " " : "", decision.reason().code());
	}

	private void count(String action, Decision decision) {
		MeterRegistry registry = this.meters.getIfAvailable();
		if (registry == null) {
			return;
		}
		Counter.builder(METRIC)
			.description("Authorization decisions by outcome, reason and action")
			.tag("outcome", decision.allowed() ? "allow" : "deny")
			.tag("reason", decision.reason().code())
			.tag("action", action)
			.register(registry)
			.increment();
	}

}
