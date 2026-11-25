package com.kibootia.backend.access.domain;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Reach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An action only the system performs, such as the purge a retention job runs, is declared like any other so the code can
 * check it, but nobody can be given it: not through a role, not through the administrator role, not through a stored grant
 * (ADR 0067). These tests are for the registry; the API is in {@code GrantsApiIT}.
 */
class SystemOnlyPermissionTest {

	private static final Permission VIEW = Permission.onCollection("example.things.view", "View things");

	private static final Permission PURGE = Permission.onCollection("example.things.purge", "Remove old things")
		.asSensitive()
		.forSystemOnly();

	private final PermissionRegistry registry = new PermissionRegistry(List.of(VIEW, PURGE));

	@Test
	@DisplayName("the action is declared, so the code can check it, but it is not one a role can be given")
	void declaredButNotGrantable() {
		assertThat(this.registry.isRegistered("example.things.purge")).isTrue();
		assertThat(this.registry.all()).extracting(Permission::name).contains("example.things.view", "example.things.purge");
		assertThat(this.registry.grantable()).extracting(Permission::name).containsExactly("example.things.view");
	}

	@Test
	@DisplayName("the administrator role does not hold it either")
	void notEvenTheAdministratorHoldsIt() {
		assertThat(this.registry.administratorGrants()).containsExactly(Grant.allow("example.things.view", Reach.ALL));
	}

	@Test
	@DisplayName("a grant of it is not valid, whether it allows or denies, and a grant of an ordinary action still is")
	void noGrantOfItIsValid() {
		assertThat(this.registry.isValid(Grant.allow("example.things.purge", Reach.ALL))).isFalse();
		assertThat(this.registry.isValid(Grant.deny("example.things.purge"))).isFalse();
		assertThat(this.registry.isValid(Grant.allow("example.things.view", Reach.ALL))).isTrue();
	}

	@Test
	@DisplayName("it cannot be offered to the user role or used before the email address is verified")
	void cannotBeCombinedWithAnythingThatGivesItToPeople() {
		assertThatThrownBy(() -> Permission.onCollection("example.things.purge", "x").forSystemOnly().forEveryUser())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("system only");
		assertThatThrownBy(() -> Permission.onCollection("example.things.purge", "x").forSystemOnly().beforeVerification())
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(Set.copyOf(this.registry.userRoleGrants())).isEmpty();
	}

}
