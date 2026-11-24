package com.kibootia.backend.audit.application;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.audit.AuditPermissions;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.events.EventsProperties;
import com.kibootia.backend.platform.events.FailedEventAdministration;
import com.kibootia.backend.platform.jobs.RetentionPurgeable;

/**
 * Removes events that failed for good (ADR 0059). An event whose retries are exhausted stays in the failed-event view so
 * that an administrator can retry it; after {@code kibootia.events.failed-retention-days} it is removed, and the removal
 * is audited, because the event is a record that something was not done. An event that is still being retried is never
 * removed.
 */
@Service
class FailedEventRetention implements RetentionPurgeable {

	private static final Logger log = LoggerFactory.getLogger(FailedEventRetention.class);

	private final FailedEventAdministration events;

	private final AuditLog auditLog;

	private final Clock clock;

	private final EventsProperties properties;

	FailedEventRetention(FailedEventAdministration events, AuditLog auditLog, Clock clock,
			EventsProperties properties) {
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
		this.properties = properties;
	}

	@Override
	@NoAuthorization("returns a constant label for the logs")
	public String name() {
		return "failed-events";
	}

	/** Removes the tenant's exhausted failed events older than the retention period and returns how many. */
	@Override
	@Transactional
	@RequiresPermission(AuditPermissions.PURGE_FAILED_EVENTS)
	public int purge() {
		int days = this.properties.failedRetentionDays();
		int removed = this.events.purgeExhaustedBefore(this.clock.instant().minus(Duration.ofDays(days)));
		if (removed > 0) {
			this.auditLog.record(AuditEntry.of("audit.failed-events.purged", "failed-event", null)
				.withChange("removed", String.valueOf(removed))
				.withChange("olderThanDays", String.valueOf(days)));
			log.warn("Removed {} failed events older than {} days; their handlers never completed", removed, days);
		}
		return removed;
	}

}
