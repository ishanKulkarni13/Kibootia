package com.kibootia.backend.access.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * The administrator and user roles (ADR 0025, ADR 0041, ADR 0054). They are created at startup in the default tenant.
 * The administrator role is kept complete: an allow, reaching every record, for every declared action. The user role is
 * offered each action a module flags for it, with the reach the module declares, once, so an administrator's removals
 * stay removed. Use cases that need one of them call {@link #admin()} or {@link #user()}, which create it first if it is
 * missing, so a database that lost the rows heals itself instead of refusing every sign-in.
 *
 * <p>At startup it also converts what an installation of contract version 1 still has in {@code role_permission}
 * ({@link #legacyGrant}), before the built-in roles are seeded.
 */
@Component
class BuiltInRoles implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BuiltInRoles.class);

	/** The suffix contract version 1 used for "every record" (ADR 0049). */
	static final String LEGACY_ANY_SUFFIX = "-any";

	private final RoleStore roles;

	private final PermissionRegistry registry;

	private final TransactionTemplate transactions;

	BuiltInRoles(RoleStore roles, PermissionRegistry registry, PlatformTransactionManager transactionManager) {
		this.roles = roles;
		this.registry = registry;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** Startup: the tenant is bound before the transaction starts, because Hibernate reads it when a session opens. */
	@Override
	@NoAuthorization("startup work before any user is signed in")
	public void run(ApplicationArguments arguments) {
		TenantContext.runAs(TenantContext.DEFAULT_TENANT, () -> this.transactions.executeWithoutResult((status) -> {
			int converted = this.roles.convertLegacyPermissions((stored) -> legacyGrant(this.registry, stored));
			if (converted > 0) {
				log.info("Converted {} stored permissions of contract version 1 into grants", converted);
			}
			ensure();
			log.info("The built-in roles are in place");
		}));
	}

	/**
	 * The grant a permission name stored by contract version 1 stands for (milestone 8, section 7), or empty for a name
	 * no module declares now:
	 * <ul>
	 * <li>{@code x-any} meant every record: a grant of {@code x} reaching every record;</li>
	 * <li>{@code x} meant only the holder's own records exactly when {@code x} had an {@code -any} sibling, and the
	 * actions that had one are the actions on a record whose type has an {@code owner}: a grant reaching the records the
	 * holder owns;</li>
	 * <li>any other {@code x} meant every record, as an administrative action.</li>
	 * </ul>
	 * {@code LegacyGrantEquivalenceTest} checks that every decision is the same before and after.
	 */
	static Optional<Grant> legacyGrant(PermissionRegistry registry, String stored) {
		String action = stored.endsWith(LEGACY_ANY_SUFFIX)
				? stored.substring(0, stored.length() - LEGACY_ANY_SUFFIX.length()) : stored;
		Optional<Permission> declared = registry.find(action);
		if (declared.isEmpty()) {
			return Optional.empty();
		}
		if (stored.endsWith(LEGACY_ANY_SUFFIX)) {
			return Optional.of(Grant.allow(action, Reach.ALL));
		}
		boolean ownable = registry.reachesOf(action).contains(Reach.relation(ResourceRef.OWNER));
		return Optional.of(Grant.allow(action, ownable ? Reach.owner() : Reach.ALL));
	}

	/** In the caller's transaction and tenant. */
	Role admin() {
		return find(Role.BuiltIn.ADMIN);
	}

	/** In the caller's transaction and tenant. */
	Role user() {
		return find(Role.BuiltIn.USER);
	}

	private void ensure() {
		this.roles.ensureBuiltInRoles(this.registry.administratorGrants(), this.registry.userRoleGrants());
	}

	private Role find(Role.BuiltIn which) {
		return this.roles.findBuiltIn(which).orElseGet(() -> {
			ensure();
			return this.roles.findBuiltIn(which).orElseThrow();
		});
	}

}
