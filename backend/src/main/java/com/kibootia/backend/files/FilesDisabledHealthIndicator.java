package com.kibootia.backend.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Says so when the files module is switched off while files still exist (ADR 0059). With the module off, nothing
 * removes a user's files when the user is erased, and nothing purges deleted ones, so switching it off after files exist
 * is unsupported: remove the files first (recipe: disable files). This guard exists only while the module is off, logs
 * a warning at startup and shows the number in the health details. It does not make the application unhealthy: the
 * application works, it only keeps what it can no longer clean up.
 */
@Component
@ConditionalOnProperty(name = "kibootia.files.enabled", havingValue = "false")
class FilesDisabledHealthIndicator implements HealthIndicator {

	private static final Logger log = LoggerFactory.getLogger(FilesDisabledHealthIndicator.class);

	private final JdbcTemplate jdbc;

	FilesDisabledHealthIndicator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@EventListener(ApplicationReadyEvent.class)
	void warnAtStartup() {
		long stored = storedFiles();
		if (stored > 0) {
			log.warn("The files module is switched off but {} stored files remain. Nothing will remove them when their "
					+ "owners are erased or the files are deleted. Switch the module on, or remove the files first.",
					stored);
		}
	}

	@Override
	public Health health() {
		try {
			long stored = storedFiles();
			Health.Builder health = Health.up().withDetail("module", "switched off").withDetail("storedFiles", stored);
			if (stored > 0) {
				health.withDetail("warning", "Stored files remain that nothing can clean up while the module is off.");
			}
			return health.build();
		}
		catch (RuntimeException ex) {
			return Health.unknown().withException(ex).build();
		}
	}

	private long storedFiles() {
		// sql: every tenant, the question is whether the table holds any row at all
		Long stored = this.jdbc.queryForObject("select count(*) from stored_file", Long.class);
		return (stored != null) ? stored : 0;
	}

}
