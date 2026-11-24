package com.kibootia.backend.access;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Resource;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.fixtures.WidgetFixtures;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The measured cost of a decision (acceptance criterion 4 of milestone 7, criterion 6 of milestone 8): how many SQL
 * statements one decision sends, counted on the calling thread. This is the baseline later phases must not exceed, and
 * the evidence for the claim that there is no cache because there is no need for one yet (ADR 0044).
 */
@Import(DecisionQueryCountIT.Counting.class)
class DecisionQueryCountIT extends AdminApiTest {

	/** Statements prepared or created on this thread since {@link #start}. */
	private static final ThreadLocal<int[]> STATEMENTS = new ThreadLocal<>();

	static void start() {
		STATEMENTS.set(new int[1]);
	}

	static int counted() {
		return STATEMENTS.get()[0];
	}

	/** Wraps the data source so every statement a connection makes is counted for the thread that makes it. */
	@TestConfiguration(proxyBeanMethods = false)
	static class Counting {

		@Bean
		static BeanPostProcessor countStatements() {
			return new BeanPostProcessor() {

				@Override
				public Object postProcessAfterInitialization(Object bean, String name) {
					if (!(bean instanceof DataSource target)) {
						return bean;
					}
					return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
							(proxy, method, args) -> {
								Object result = invoke(method, target, args);
								return (result instanceof Connection connection) ? counting(connection) : result;
							});
				}

			};
		}

		private static Connection counting(Connection connection) {
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
					(proxy, method, args) -> {
						String name = method.getName();
						int[] count = STATEMENTS.get();
						if (count != null && (name.startsWith("prepare") || name.equals("createStatement"))) {
							count[0]++;
						}
						return invoke(method, connection, args);
					});
		}

		private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
			try {
				return method.invoke(target, args);
			}
			catch (InvocationTargetException ex) {
				throw ex.getCause();
			}
		}

	}

	@Autowired
	Authorization authorization;

	@AfterEach
	void stop() {
		STATEMENTS.remove();
		SecurityContextHolder.clearContext();
	}

	record Widget(UUID id, UUID ownerId) implements Resource {

		@Override
		public ResourceRef resourceRef() {
			return ResourceRef.of(WidgetFixtures.WIDGET, this.id, TenantContext.DEFAULT_TENANT).ownedBy(this.ownerId);
		}

	}

	private <T> int statementsFor(Callable<T> decision) {
		Jwt token = Jwt.withTokenValue("t").header("alg", "none").subject(ALICE.toString()).claim("email_verified", true).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
		return TenantContext.callAs(TenantContext.DEFAULT_TENANT, () -> {
			start();
			decision.call();
			return counted();
		});
	}

	@Test
	@DisplayName("one decision is one SQL statement: on the collection, on a record, and for a list's access filter")
	void oneStatementPerDecision() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		assertThat(statementsFor(() -> this.authorization.decide(WidgetFixtures.CREATE))).isEqualTo(1);
		assertThat(statementsFor(() -> this.authorization.decide(WidgetFixtures.UPDATE,
				new Widget(UUID.randomUUID(), ALICE)))).isEqualTo(1);
		assertThat(statementsFor(() -> this.authorization.filter(WidgetFixtures.VIEW))).isEqualTo(1);
		// A refusal costs the same: the decision record and the metric touch no database.
		assertThat(statementsFor(() -> this.authorization.decide(WidgetFixtures.RESTORE))).isEqualTo(1);
	}

}
