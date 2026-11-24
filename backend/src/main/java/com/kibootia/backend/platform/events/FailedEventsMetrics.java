package com.kibootia.backend.platform.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.stereotype.Component;

/**
 * Reports how many events have failed (ADR 0059), so that an alert can fire before an administrator looks at the view.
 * The value is read from the registry when the metric is scraped. It counts every event that has failed and has not been
 * delivered, whether or not its retries are exhausted.
 */
@Component
class FailedEventsMetrics implements MeterBinder {

	/** The gauge's name. */
	static final String FAILED = "kibootia.events.failed";

	private final FailedEventAdministration events;

	FailedEventsMetrics(FailedEventAdministration events) {
		this.events = events;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder(FAILED, this.events, FailedEventAdministration::countFailed)
			.description("Events whose delivery failed and that have not been delivered since")
			.register(registry);
	}

}
