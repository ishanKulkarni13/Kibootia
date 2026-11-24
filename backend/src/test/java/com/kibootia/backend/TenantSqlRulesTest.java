package com.kibootia.backend;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Architecture rule 14: no hand-written SQL forgets the tenant (finding S7). Shown to fire on statements made to break it. */
class TenantSqlRulesTest {

	@Test
	@DisplayName("every hand-written statement in the main sources names the tenant")
	void mainSourcesNameTheTenant() {
		assertThat(TenantSqlRules.check(Path.of("src/main/java"))).isEmpty();
	}

	@Test
	@DisplayName("fires on a read, a change, a delete and an insert that forget the tenant")
	void firesOnForgottenTenant() {
		String source = """
				class Store {
					void a() { jdbc.sql("select id from note where owner_id = :owner"); }
					void b() { jdbc.sql(\"\"\"
							update user_profile set status = 'X'
							where id = :id
							\"\"\"); }
					void c() { jdbc.sql("delete from role_assignment where user_id = :user"); }
					void d() { jdbc.sql("insert into role_grant (role_id, action) values (:role, :action)"); }
				}
				""";
		assertThat(TenantSqlRules.check("Store.java", source)).hasSize(4)
			.anySatisfy((violation) -> assertThat(violation).startsWith("Store.java:2:").contains("from note"))
			.anySatisfy((violation) -> assertThat(violation).contains("update user_profile"));
	}

	@Test
	@DisplayName("passes statements that name the tenant, a tenant-first builder, a marked migration, and other tables")
	void passesWhatIsAllowed() {
		String source = """
				class Store {
					void a() { jdbc.sql("select id from note where tenant_id = :tenant and owner_id = :owner"); }
					void b() { jdbc.sql("insert ignore into role_grant (tenant_id, role_id) values (:tenant, :role)"); }
					void c() {
						StringBuilder where = new StringBuilder("tenant_id = :tenant");
						jdbc.sql("select count(*) from user_profile where " + where);
					}
					void d() {
						// sql: every tenant, a data migration that copies each row's own tenant
						jdbc.sql("select tenant_id, role_id, permission from role_permission");
					}
					void e() { jdbc.sql("delete from event_publication where completion_date < ?"); }
					@Query("select n from NoteEntity n where n.id = :id") Object f();
				}
				""";
		assertThat(TenantSqlRules.check("Store.java", source)).isEmpty();
	}

	@Test
	@DisplayName("a where builder that does not start with the tenant does not excuse an open where")
	void builderMustStartWithTheTenant() {
		String source = """
				class Store {
					void c() {
						StringBuilder where = new StringBuilder("deleted_at is null");
						jdbc.sql("select count(*) from user_profile where " + where);
					}
				}
				""";
		assertThat(TenantSqlRules.check("Store.java", source)).hasSize(1);
	}

}
