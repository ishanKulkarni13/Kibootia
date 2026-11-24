package com.kibootia.backend;

import java.util.List;
import java.util.UUID;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.files.application.ObjectStorage;
import com.kibootia.backend.identity.application.IdentityProviderAdmin;
import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.DurableEventListener;
import com.kibootia.backend.platform.events.EventContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves rule 13 fails for the mistakes it exists for and passes for correct code (ADR 0058). */
class ExternalCallRulesTest {

	record SomeEvent(EventContext context) implements DomainEvent {

	}

	static class Violations {

		static class CallsKeycloakInATransaction {

			private IdentityProviderAdmin keycloak;

			@Transactional
			void deletesInsideATransaction(UUID id) {
				this.keycloak.delete(id);
			}

		}

		@Transactional
		static class CallsStorageInATransactionalClass {

			private ObjectStorage storage;

			void deletesInsideATransactionalClass(List<String> keys) {
				this.storage.deleteAll(keys);
			}

		}

		static class HandlerInThePlatformTransaction {

			private IdentityProviderAdmin keycloak;

			@DurableEventListener
			void handlesWithTheDefaultTransaction(SomeEvent event) {
				this.keycloak.find(event.context().eventId());
			}

		}

		static class HandlerOfAnotherSystemThatAsksForATransaction {

			private ObjectStorage storage;

			@DurableEventListener(transactional = true)
			void asksForOneExplicitly(SomeEvent event) {
				this.storage.deleteAll(List.of());
			}

		}

	}

	static class Correct {

		static class HandlerWithoutATransaction {

			private IdentityProviderAdmin keycloak;

			@DurableEventListener(transactional = false)
			void on(SomeEvent event) {
				this.keycloak.delete(event.context().eventId());
			}

			void callsOutsideAnyTransaction(UUID id) {
				this.keycloak.delete(id);
			}

		}

		static class TransactionalButOnlyTheDatabase {

			@Transactional
			void changesOnlyTheDatabase() {
			}

		}

		static class HandlerThatOnlyUsesTheDatabase {

			@DurableEventListener
			void on(SomeEvent event) {
			}

		}

	}

	private static JavaClasses of(Class<?>... classes) {
		return new ClassFileImporter().importClasses(classes);
	}

	@Test
	@DisplayName("rule 13 reports a transactional method that calls Keycloak, and a method of a transactional class that calls storage")
	void transactionalCallsAreReported() {
		JavaClasses violations = of(Violations.CallsKeycloakInATransaction.class,
				Violations.CallsStorageInATransactionalClass.class);

		assertThatThrownBy(() -> ExternalCallRules.transactionalMethodsDoNotCallOtherSystems().check(violations))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("deletesInsideATransaction")
			.hasMessageContaining("deletesInsideATransactionalClass");
	}

	@Test
	@DisplayName("rule 13 reports a durable handler that keeps the platform's transaction while its class uses Keycloak or storage")
	void handlersInATransactionAreReported() {
		JavaClasses violations = of(Violations.HandlerInThePlatformTransaction.class,
				Violations.HandlerOfAnotherSystemThatAsksForATransaction.class);

		assertThatThrownBy(() -> ExternalCallRules.handlersThatUseOtherSystemsRunWithoutATransaction().check(violations))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("handlesWithTheDefaultTransaction")
			.hasMessageContaining("asksForOneExplicitly");
	}

	@Test
	@DisplayName("correct code passes: a handler without a transaction, calls outside one, a handler that only uses the database")
	void correctCodePasses() {
		JavaClasses correct = of(Correct.HandlerWithoutATransaction.class, Correct.TransactionalButOnlyTheDatabase.class,
				Correct.HandlerThatOnlyUsesTheDatabase.class);

		assertThatCode(() -> {
			ExternalCallRules.transactionalMethodsDoNotCallOtherSystems().check(correct);
			ExternalCallRules.handlersThatUseOtherSystemsRunWithoutATransaction().check(correct);
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("the ports the rule names exist")
	void thePortsExist() {
		assertThat(ExternalCallRules.OTHER_SYSTEMS).containsExactly(IdentityProviderAdmin.class.getName(),
				ObjectStorage.class.getName());
	}

}
