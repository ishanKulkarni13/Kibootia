package com.kibootia.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An installation that is already running keeps working after an upgrade (the rule every phase of the authorization roadmap
 * ends with, and R-15 of the architecture assessment): the migrations run on a database that has data, not only on an empty
 * one. The empty case is {@code BackendApplicationIT}.
 *
 * <p>This test has a MySQL of its own, so it can make databases of its own and does not disturb the others. It
 * <ol>
 * <li>applies the migrations one version at a time, which finds a migration that depends on another module's table being
 * there first;</li>
 * <li>stops just before the newest migrations of each kind, writes the rows a running installation has, including rows
 * that the new constraints would refuse, and applies the rest.</li>
 * </ol>
 * When a migration is added, add what it does to existing data to the second test.
 */
class MigrationUpgradeIT {

	private static final String[] LOCATIONS = { "classpath:db/migration/platform", "classpath:db/migration/audit",
			"classpath:db/migration/access", "classpath:db/migration/identity", "classpath:db/migration/files",
			"classpath:db/migration/notes" };

	/** The last migration before the foreign keys and the {@code TEXT} event column were added. */
	private static final String BEFORE_INTEGRITY = "202611241000";

	@SuppressWarnings({ "deprecation", "resource" })
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	static {
		MYSQL.withCommand("--performance-schema=OFF", "--innodb-buffer-pool-size=64M");
	}

	@BeforeAll
	static void start() {
		MYSQL.start();
	}

	@AfterAll
	static void stop() {
		MYSQL.stop();
	}

	private static Connection root() throws SQLException {
		return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
	}

	/** A new, empty database, and a Flyway for it limited to a version. */
	private static Flyway flyway(String database, String target) throws SQLException {
		try (Connection connection = root(); Statement statement = connection.createStatement()) {
			statement.execute("create database if not exists " + database);
		}
		String url = MYSQL.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
		return Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).locations(LOCATIONS).target(target).load();
	}

	private static Connection connectTo(String database) throws SQLException {
		return DriverManager.getConnection(MYSQL.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1"), "root",
				MYSQL.getPassword());
	}

	private static byte[] bytes(UUID id) {
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(new byte[16]);
		buffer.putLong(id.getMostSignificantBits());
		buffer.putLong(id.getLeastSignificantBits());
		return buffer.array();
	}

	private static int count(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
			rows.next();
			return rows.getInt(1);
		}
	}

	@Test
	@DisplayName("every migration applies on top of the one before it, version by version, from an empty database")
	void everyVersionAppliesOnTopOfThePreviousOne() throws Exception {
		Flyway all = flyway("upgrade_stepwise", "latest");
		List<String> versions = new ArrayList<>();
		for (MigrationInfo migration : all.info().pending()) {
			versions.add(migration.getVersion().getVersion());
		}
		assertThat(versions).as("the versions Flyway found").hasSizeGreaterThanOrEqualTo(10).isSorted();

		for (String version : versions) {
			Flyway step = flyway("upgrade_stepwise", version);
			assertThat(step.migrate().success).as("migrating up to " + version).isTrue();
		}

		Flyway done = flyway("upgrade_stepwise", "latest");
		assertThat(done.info().pending()).as("nothing is left to apply").isEmpty();
		assertThat(done.validateWithResult().validationSuccessful).isTrue();
	}

	@Test
	@DisplayName("an installation with data upgrades: rows of roles that are gone are removed, the rest is kept, and a long event stays intact")
	void aRunningInstallationUpgrades() throws Exception {
		Flyway before = flyway("upgrade_with_data", BEFORE_INTEGRITY);
		before.migrate();

		UUID tenant = UUID.fromString("00000000-0000-7000-8000-000000000001");
		UUID editor = UUID.randomUUID();
		UUID gone = UUID.randomUUID();
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		String longPayload = "{\"context\":{\"tenantId\":\"" + tenant + "\"},\"label\":\"" + "x".repeat(3800) + "\"}";
		try (Connection connection = connectTo("upgrade_with_data")) {
			try (PreparedStatement role = connection.prepareStatement(
					"insert into role (id, tenant_id, version, created_at, created_by, updated_at, updated_by, name, description) "
							+ "values (?, ?, 0, utc_timestamp(6), ?, utc_timestamp(6), ?, 'Editor', 'edits')")) {
				role.setBytes(1, bytes(editor));
				role.setBytes(2, bytes(tenant));
				role.setBytes(3, bytes(alice));
				role.setBytes(4, bytes(alice));
				role.executeUpdate();
			}
			// What a running installation of that version holds: a held role, and rows of a role that no longer exists.
			insertPermission(connection, tenant, editor, "notes.notes.view");
			insertPermission(connection, tenant, gone, "notes.notes.view");
			insertPermission(connection, tenant, gone, "notes.notes.update");
			insertAssignment(connection, tenant, alice, editor);
			insertAssignment(connection, tenant, bob, gone);
			try (PreparedStatement event = connection.prepareStatement(
					"insert into event_publication (id, listener_id, event_type, serialized_event, publication_date, status, "
							+ "completion_attempts) values (?, 'x.Y#on', 'x.Z', ?, utc_timestamp(6), 'PUBLISHED', 0)")) {
				event.setBytes(1, bytes(UUID.randomUUID()));
				event.setString(2, longPayload);
				event.executeUpdate();
			}
		}

		Flyway after = flyway("upgrade_with_data", "latest");
		assertThat(after.migrate().success).isTrue();

		try (Connection connection = connectTo("upgrade_with_data")) {
			assertThat(count(connection, "select count(*) from role_permission")).as("permissions of the role that exists").isEqualTo(1);
			assertThat(count(connection, "select count(*) from role_assignment")).as("assignments of the role that exists").isEqualTo(1);
			assertThat(count(connection, "select count(*) from role")).isEqualTo(1);
			assertThat(count(connection, "select char_length(serialized_event) from event_publication")).as("the long event is intact")
				.isEqualTo(longPayload.length());
			assertThat(count(connection, """
					select count(*) from information_schema.columns where table_schema = database()
					  and table_name = 'event_publication' and column_name = 'serialized_event' and data_type = 'text'
					""")).as("the event column is now TEXT").isEqualTo(1);
			assertThat(count(connection, """
					select count(*) from information_schema.table_constraints where table_schema = database()
					  and constraint_type = 'FOREIGN KEY'
					  and constraint_name in ('fk_role_assignment_role', 'fk_role_permission_role', 'fk_role_grant_role')
					""")).as("the role tables have their keys").isEqualTo(3);
		}
	}

	private static void insertPermission(Connection connection, UUID tenant, UUID role, String permission) throws SQLException {
		try (PreparedStatement statement = connection
			.prepareStatement("insert into role_permission (tenant_id, role_id, permission) values (?, ?, ?)")) {
			statement.setBytes(1, bytes(tenant));
			statement.setBytes(2, bytes(role));
			statement.setString(3, permission);
			statement.executeUpdate();
		}
	}

	private static void insertAssignment(Connection connection, UUID tenant, UUID user, UUID role) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"insert into role_assignment (tenant_id, user_id, role_id, assigned_at, assigned_by) values (?, ?, ?, utc_timestamp(6), ?)")) {
			statement.setBytes(1, bytes(tenant));
			statement.setBytes(2, bytes(user));
			statement.setBytes(3, bytes(role));
			statement.setBytes(4, bytes(user));
			statement.executeUpdate();
		}
	}

}
