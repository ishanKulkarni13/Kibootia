package com.kibootia.backend.audit.application;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.platform.jobs.RetentionPurgeJob;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Events that failed for good are removed after the retention period, and the removal is audited (ADR 0059). The tests
 * write the registry's rows directly, because what matters is which rows the purge picks. Retries are exhausted at 4
 * attempts in these tests (see {@link AdminApiTest}); the retention period is the default, 90 days.
 */
class FailedEventRetentionIT extends AdminApiTest {

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

	@Autowired
	RetentionPurgeJob purge;

	private void failedEvent(UUID tenant, int attempts, int daysOld) {
		this.jdbc.update("""
				insert into event_publication (id, listener_id, event_type, serialized_event, publication_date,
				  completion_date, status, completion_attempts, last_resubmission_date)
				values (?, 'com.example.Handler#on', 'com.example.SomethingHappened', ?, ?, null, 'FAILED', ?, null)
				""", UuidBytes.toBytes(UUID.randomUUID()), "{\"context\":{\"tenantId\":\"" + tenant + "\"}}",
				LocalDateTime.now(ZoneOffset.UTC).minusDays(daysOld), attempts);
	}

	private int remaining() {
		return this.jdbc.queryForObject("select count(*) from event_publication", Integer.class);
	}

	@Test
	@DisplayName("exhausted failed events older than the retention are removed; events still retried, recent ones and other tenants' are kept")
	void removesOnlyWhatIsExhaustedAndOld() {
		failedEvent(TenantContext.DEFAULT_TENANT, 4, 100);
		failedEvent(TenantContext.DEFAULT_TENANT, 4, 91);
		failedEvent(TenantContext.DEFAULT_TENANT, 2, 100);
		failedEvent(TenantContext.DEFAULT_TENANT, 4, 10);
		failedEvent(OTHER_TENANT, 4, 100);

		this.purge.runOnce();

		assertThat(remaining()).as("one still retried, one recent, one of another tenant").isEqualTo(3);
		assertThat(this.jdbc.queryForObject("select count(*) from event_publication where completion_attempts = 2",
				Integer.class)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"select count(*) from event_publication where publication_date > utc_timestamp(6) - interval 20 day",
				Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("the removal is audited with how many events went and how old they were")
	void theRemovalIsAudited() {
		failedEvent(TenantContext.DEFAULT_TENANT, 4, 100);
		failedEvent(TenantContext.DEFAULT_TENANT, 5, 120);

		this.purge.runOnce();

		assertThat(this.jdbc.queryForObject(
				"select count(*) from audit_entry where action = 'audit.failed-events.purged'", Integer.class))
			.isEqualTo(1);
		String changes = this.jdbc.queryForObject(
				"select changes from audit_entry where action = 'audit.failed-events.purged'", String.class);
		assertThat(changes).contains("removed").contains("2").contains("olderThanDays").contains("90");
	}

	@Test
	@DisplayName("a purge that finds nothing leaves no audit entry")
	void nothingRemovedMeansNothingAudited() {
		failedEvent(TenantContext.DEFAULT_TENANT, 4, 10);

		this.purge.runOnce();

		assertThat(remaining()).isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"select count(*) from audit_entry where action = 'audit.failed-events.purged'", Integer.class))
			.isZero();
	}

}
