package com.kibootia.backend.access.persistence;

import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Principal;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.persistence.UuidBytes;

/**
 * A user's grants from the roles stored in the database (ADR 0025, ADR 0044, ADR 0054). Each call is one indexed query
 * on the user's assignments, so a role change, a removal or a disabled account applies on the very next request, on any
 * number of application instances, with no cache to invalidate.
 *
 * <p>The administrator role holds explicit grants like any other role, kept complete at startup ({@code BuiltInRoles}),
 * so nothing here treats it specially. A stored grant that no longer fits the declarations (an action a removed module
 * declared, a reach its type no longer has) is left out, so removing a module cannot leave a role pointing at something
 * that does not exist.
 */
@Component
class StoredPermissionSource implements PermissionSource {

	private static final Logger log = LoggerFactory.getLogger(StoredPermissionSource.class);

	private final JdbcClient jdbc;

	private final PermissionRegistry registry;

	StoredPermissionSource(JdbcClient jdbc, PermissionRegistry registry) {
		this.jdbc = jdbc;
		this.registry = registry;
	}

	@Override
	public Set<Grant> grantsOf(Principal.User user) {
		Set<Grant> grants = new LinkedHashSet<>();
		this.jdbc.sql("""
				select rg.action as action, rg.reach as reach, rg.effect as effect
				from role_assignment ra
				join role r on r.id = ra.role_id and r.tenant_id = ra.tenant_id and r.deleted_at is null
				join role_grant rg on rg.role_id = r.id and rg.tenant_id = r.tenant_id
				where ra.tenant_id = :tenant and ra.user_id = :user
				""")
			.param("tenant", UuidBytes.toBytes(user.tenantId()))
			.param("user", UuidBytes.toBytes(user.id()))
			.query((rs, row) -> {
				try {
					Grant grant = new Grant(rs.getString("action"), Reach.parse(rs.getString("reach")),
							Grant.Effect.valueOf(rs.getString("effect")));
					if (this.registry.isValid(grant)) {
						grants.add(grant);
					}
				}
				catch (IllegalArgumentException ex) {
					log.warn("Ignoring a stored grant that cannot be read: {}", ex.getMessage());
				}
				return null;
			})
			.list();
		return grants;
	}

}
