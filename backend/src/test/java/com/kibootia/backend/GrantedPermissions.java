package com.kibootia.backend;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Reach;

/**
 * Lets a test act with grants of its choosing, without roles. On top of the grants the built-in {@code user} role is
 * offered, a caller holds whatever the test has {@link #grant granted}, less what it has {@link #revoke revoked}. Import
 * it into a test; call {@link #reset} after each test.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GrantedPermissions {

	private static final Set<Grant> GRANTED = ConcurrentHashMap.newKeySet();

	private static final Set<String> REVOKED = ConcurrentHashMap.newKeySet();

	/** Grants each action reaching every record (contract version 1 called this holding {@code <action>-any}). */
	public static void grant(String... actions) {
		for (String action : actions) {
			GRANTED.add(Grant.allow(action, Reach.ALL));
		}
	}

	public static void grant(Grant... grants) {
		GRANTED.addAll(Set.of(grants));
	}

	/** Takes actions away entirely, including ones the built-in {@code user} role holds. */
	public static void revoke(String... actions) {
		REVOKED.addAll(Set.of(actions));
	}

	public static void reset() {
		GRANTED.clear();
		REVOKED.clear();
	}

	@Bean
	@Primary
	PermissionSource grantedPermissionSource(PermissionRegistry registry) {
		return (user) -> {
			Set<Grant> held = new LinkedHashSet<>(registry.userRoleGrants());
			held.addAll(GRANTED);
			held.removeIf((grant) -> REVOKED.contains(grant.action()));
			return held;
		};
	}

}
