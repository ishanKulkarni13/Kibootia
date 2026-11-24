package com.kibootia.backend;

import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.platform.events.DurableEventListener;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Architecture rule 13 (ADR 0058): no database transaction is open while another system is being called. A slow Keycloak
 * or storage server would otherwise hold a connection from a small pool, and a request waiting for a connection would
 * wait for Keycloak.
 *
 * <p>The systems are named by their ports, as text, so that the rule does not depend on the modules that own them:
 * {@value #IDENTITY_PROVIDER} and {@value #OBJECT_STORAGE}. A new port to a system outside the database is added to
 * {@link #OTHER_SYSTEMS}.
 *
 * <p>What the rule sees: a call written inside a transactional method, and a durable handler in a class that holds such a
 * port. What it does not see: a transactional method that reaches the port through another method, the way rule 7 does
 * not see a check inside a lambda. The handlers are covered by the second half, which is where the mistake was made.
 */
final class ExternalCallRules {

	static final String IDENTITY_PROVIDER = "com.kibootia.backend.identity.application.IdentityProviderAdmin";

	static final String OBJECT_STORAGE = "com.kibootia.backend.files.application.ObjectStorage";

	static final List<String> OTHER_SYSTEMS = List.of(IDENTITY_PROVIDER, OBJECT_STORAGE);

	private ExternalCallRules() {
	}

	/** Rule 13, first half: a transactional method does not call another system. */
	static ArchRule transactionalMethodsDoNotCallOtherSystems() {
		return methods().that(areTransactional())
			.should(notCallOtherSystems())
			.allowEmptyShould(true)
			.because("no database transaction may be open while another system is called (ADR 0058)");
	}

	/**
	 * Rule 13, second half: a durable handler in a class that holds a port to another system runs without the platform's
	 * transaction, because that transaction would be open for the whole call.
	 */
	static ArchRule handlersThatUseOtherSystemsRunWithoutATransaction() {
		return methods().that()
			.areAnnotatedWith(DurableEventListener.class)
			.should(runWithoutATransactionWhenTheClassUsesOtherSystems())
			.allowEmptyShould(true)
			.because("the platform's handler transaction would be open for the whole call to the other system (ADR 0058)");
	}

	private static DescribedPredicate<JavaMethod> areTransactional() {
		return DescribedPredicate.describe("are @Transactional, or are declared in a @Transactional class",
				(method) -> method.isAnnotatedWith(Transactional.class)
						|| method.getOwner().isAnnotatedWith(Transactional.class));
	}

	private static ArchCondition<JavaMethod> notCallOtherSystems() {
		return new ArchCondition<>("not call another system") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
					if (isOtherSystem(call.getTargetOwner())) {
						events.add(SimpleConditionEvent.violated(method, method.getFullName() + " is transactional and calls "
								+ call.getTarget().getFullName() + " (" + call.getSourceCodeLocation() + ")"));
					}
				}
			}
		};
	}

	private static ArchCondition<JavaMethod> runWithoutATransactionWhenTheClassUsesOtherSystems() {
		return new ArchCondition<>("declare transactional = false when its class holds a port to another system") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				boolean usesOtherSystem = method.getOwner()
					.getAllFields()
					.stream()
					.anyMatch((field) -> isOtherSystem(field.getRawType()));
				boolean transactional = method.getAnnotationOfType(DurableEventListener.class).transactional();
				if (usesOtherSystem && transactional) {
					events.add(SimpleConditionEvent.violated(method, method.getFullName()
							+ " handles an event in a class that uses another system, so it must be declared "
							+ "@DurableEventListener(transactional = false) (" + method.getSourceCodeLocation() + ")"));
				}
			}
		};
	}

	private static boolean isOtherSystem(JavaClass type) {
		return OTHER_SYSTEMS.stream().anyMatch(type::isAssignableTo);
	}

}
