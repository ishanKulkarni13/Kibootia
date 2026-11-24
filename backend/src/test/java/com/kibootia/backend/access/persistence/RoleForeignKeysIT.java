package com.kibootia.backend.access.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A permission or an assignment cannot name a role that does not exist (ADR 0059), and the migration that adds the keys
 * removes the rows that already did. The tests write rows directly: the point is what the database refuses, not what the
 * application would do.
 */
class RoleForeignKeysIT extends AdminApiTest {

	private static final String MIGRATION = "db/migration/access/V202611250900__access_role_foreign_keys.sql";

	private static final String KEYS = """
			select count(*) from information_schema.table_constraints
			where constraint_schema = database() and constraint_type = 'FOREIGN KEY'
			  and constraint_name in ('fk_role_assignment_role', 'fk_role_permission_role')
			""";

	private static final byte[] TENANT = UuidBytes.toBytes(TenantContext.DEFAULT_TENANT);

	private UUID role(String name) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				insert into role (id, tenant_id, version, created_at, created_by, updated_at, updated_by, name, description)
				values (?, ?, 0, utc_timestamp(6), ?, utc_timestamp(6), ?, ?, 'a role for a test')
				""", UuidBytes.toBytes(id), TENANT, UuidBytes.toBytes(ADMIN), UuidBytes.toBytes(ADMIN), name);
		return id;
	}

	private void assign(UUID user, UUID role) {
		this.jdbc.update("""
				insert into role_assignment (tenant_id, user_id, role_id, assigned_at, assigned_by)
				values (?, ?, ?, utc_timestamp(6), ?)
				""", TENANT, UuidBytes.toBytes(user), UuidBytes.toBytes(role), UuidBytes.toBytes(ADMIN));
	}

	private void permit(UUID role, String permission) {
		this.jdbc.update("insert into role_permission (tenant_id, role_id, permission) values (?, ?, ?)", TENANT,
				UuidBytes.toBytes(role), permission);
	}

	private int rows(String table) {
		return this.jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private void runMigration() {
		this.jdbc.execute((Connection connection) -> {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
			return null;
		});
	}

	@Test
	@DisplayName("an assignment of a role that does not exist is refused")
	void assignmentOfAnUnknownRoleIsRefused() {
		assertThatThrownBy(() -> assign(ALICE, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("a permission of a role that does not exist is refused")
	void permissionOfAnUnknownRoleIsRefused() {
		assertThatThrownBy(() -> permit(UUID.randomUUID(), "notes.notes.view"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("a role that is still held, or still has permissions, cannot be removed from the table")
	void aRoleInUseCannotBeRemoved() {
		UUID editor = role("Editor");
		assign(ALICE, editor);
		permit(editor, "notes.notes.view");

		assertThatThrownBy(() -> this.jdbc.update("delete from role where id = ?", UuidBytes.toBytes(editor)))
			.isInstanceOf(DataIntegrityViolationException.class);

		this.jdbc.update("delete from role_assignment where role_id = ?", UuidBytes.toBytes(editor));
		this.jdbc.update("delete from role_permission where role_id = ?", UuidBytes.toBytes(editor));
		assertThat(this.jdbc.update("delete from role where id = ?", UuidBytes.toBytes(editor))).isEqualTo(1);
	}

	@Test
	@DisplayName("the migration removes rows of roles that are gone, keeps the rest, and leaves both keys in place")
	void theMigrationCleansOrphansAndAddsTheKeys() throws SQLException {
		UUID editor = role("Editor");
		try {
			// The state of an installation before the migration: no keys, and rows of a role that no longer exists.
			this.jdbc.execute("alter table role_assignment drop foreign key fk_role_assignment_role");
			this.jdbc.execute("alter table role_permission drop foreign key fk_role_permission_role");
			UUID gone = UUID.randomUUID();
			assign(ALICE, editor);
			assign(BOB, gone);
			permit(editor, "notes.notes.view");
			permit(gone, "notes.notes.view");
			permit(gone, "notes.notes.update");

			runMigration();

			assertThat(rows("role_assignment")).as("the assignment of the missing role is gone").isEqualTo(1);
			assertThat(rows("role_permission")).as("the permissions of the missing role are gone").isEqualTo(1);
			assertThat(this.jdbc.queryForObject(KEYS, Integer.class)).isEqualTo(2);
			assertThatThrownBy(() -> assign(CAROL, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		}
		finally {
			restoreKeys();
		}
	}

	/** If an assertion above failed half-way, the keys must still be there for the tests that follow. */
	private void restoreKeys() {
		this.jdbc.update("delete from role_assignment");
		this.jdbc.update("delete from role_permission");
		Integer keys = this.jdbc.queryForObject(KEYS, Integer.class);
		if (keys != null && keys < 2) {
			runMigration();
		}
	}

}
