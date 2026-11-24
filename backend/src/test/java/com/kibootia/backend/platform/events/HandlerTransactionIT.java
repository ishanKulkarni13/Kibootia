package com.kibootia.backend.platform.events;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.Await;
import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.events.TransactionProbe.DefaultEvent;
import com.kibootia.backend.platform.events.TransactionProbe.PlainEvent;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A durable handler that calls another system runs without a database transaction (ADR 0058): no connection is held
 * while the call is in flight, the "already handled" mark is written only after the handler has finished, a failure
 * leaves no mark, and an event that was handled before is skipped.
 */
@SpringBootTest(properties = { "kibootia.events.initial-backoff=PT0S", "kibootia.events.max-backoff=PT0S",
		"kibootia.events.max-attempts=3", "kibootia.events.stale-after=PT1H" })
@Import({ TestcontainersConfiguration.class, TransactionProbe.class })
class HandlerTransactionIT {

	private static final String PLAIN_HANDLER = TransactionProbe.class.getName() + "#on";

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	EventContextFactory contexts;

	@Autowired
	FailedEventAdministration failedEvents;

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void clean() {
		TransactionProbe.doNotWait();
		Await.until("in-flight deliveries to finish", () -> this.jdbc.queryForObject(
				"select count(*) from event_publication where completion_date is null and status in "
						+ "('PUBLISHED', 'PROCESSING', 'RESUBMITTED')", Integer.class) == 0);
		this.jdbc.update("delete from event_publication");
		this.jdbc.update("delete from processed_event");
		TransactionProbe.reset();
	}

	private UUID publish(PlainEvent event) {
		TenantContext.runAs(TenantContext.DEFAULT_TENANT,
				() -> this.transactions.executeWithoutResult((status) -> this.publisher.publishEvent(event)));
		return event.context().eventId();
	}

	private PlainEvent plain() {
		return TenantContext.callAs(TenantContext.DEFAULT_TENANT,
				() -> new PlainEvent("plain", this.contexts.capture()));
	}

	private int connectionsInUse() throws SQLException {
		HikariPoolMXBean pool = this.dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
		return pool.getActiveConnections();
	}

	private int marks() {
		return this.jdbc.queryForObject("select count(*) from processed_event where handler like ?", Integer.class,
				"%TransactionProbe#on");
	}

	@Test
	@DisplayName("a handler that opted out runs with no transaction and holds no database connection while it works")
	void aHandlerThatOptedOutHoldsNoConnection() throws Exception {
		publish(plain());

		assertThat(TransactionProbe.entered.await(10, TimeUnit.SECONDS)).as("the handler started").isTrue();
		assertThat(TransactionProbe.TRANSACTION_OPEN.get()).as("a transaction is open in the handler").isFalse();
		assertThat(connectionsInUse()).as("connections borrowed while the handler works").isZero();
		assertThat(marks()).as("the mark is written only when the handler has finished").isZero();

		TransactionProbe.doNotWait();
		Await.until("the handler to finish", () -> TransactionProbe.SUCCESSES.get() == 1);
		Await.until("the mark to be written", () -> marks() == 1);
	}

	@Test
	@DisplayName("for contrast, a default handler keeps the platform's transaction and one connection while it works")
	void aDefaultHandlerKeepsAConnection() throws Exception {
		TenantContext.runAs(TenantContext.DEFAULT_TENANT, () -> this.transactions.executeWithoutResult(
				(status) -> this.publisher.publishEvent(new DefaultEvent("default", this.contexts.capture()))));

		assertThat(TransactionProbe.entered.await(10, TimeUnit.SECONDS)).as("the handler started").isTrue();
		assertThat(TransactionProbe.TRANSACTION_OPEN.get()).isTrue();
		assertThat(connectionsInUse()).as("the mark's transaction holds a connection").isEqualTo(1);

		TransactionProbe.doNotWait();
		Await.until("the handler to finish", () -> TransactionProbe.SUCCESSES.get() == 1);
	}

	@Test
	@DisplayName("a handler that fails leaves no mark, and the retry runs it again and then records the mark")
	void aFailureLeavesNoMark() {
		TransactionProbe.doNotWait();
		TransactionProbe.FAILURES_LEFT.set(1);
		publish(plain());

		Await.until("the handler to fail", () -> failedPublications() == 1);
		assertThat(marks()).as("a failed handler leaves no mark").isZero();

		UUID publication = UuidBytes.fromBytes(this.jdbc.queryForObject("select id from event_publication", byte[].class));
		assertThat(TenantContext.callAs(TenantContext.DEFAULT_TENANT, () -> this.failedEvents.retry(publication))).isTrue();

		Await.until("the retry to succeed", () -> TransactionProbe.SUCCESSES.get() == 1);
		Await.until("the mark to be written", () -> marks() == 1);
		assertThat(TransactionProbe.CALLS.get()).as("the handler ran twice").isEqualTo(2);
	}

	@Test
	@DisplayName("an event this handler has already handled is skipped without running it")
	void anAlreadyHandledEventIsSkipped() {
		TransactionProbe.doNotWait();
		PlainEvent event = plain();
		this.jdbc.update("insert into processed_event (event_id, handler, tenant_id, processed_at) "
				+ "values (?, ?, ?, utc_timestamp(6))", UuidBytes.toBytes(event.context().eventId()), PLAIN_HANDLER,
				UuidBytes.toBytes(TenantContext.DEFAULT_TENANT));

		publish(event);

		Await.until("the event to be delivered", () -> completedPublications() == 1);
		assertThat(TransactionProbe.CALLS.get()).as("the handler did not run").isZero();
		assertThat(marks()).isEqualTo(1);
	}

	private int failedPublications() {
		return this.jdbc.queryForObject(
				"select count(*) from event_publication where completion_date is null and status = 'FAILED'",
				Integer.class);
	}

	private int completedPublications() {
		return this.jdbc.queryForObject("select count(*) from event_publication where completion_date is not null",
				Integer.class);
	}

}
