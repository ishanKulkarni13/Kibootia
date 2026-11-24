package com.kibootia.backend.access.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kibootia.backend.platform.authorization.Grant;

/**
 * Where roles, what they allow and who holds them are kept. The use cases depend on this port, never on JPA or SQL
 * (ADR 0003). Everything acts on the current tenant. Taking a grant off a role and taking a role off a user delete
 * the row; the audit log keeps the history (ADR 0050).
 */
public interface RoleStore {

	Role insert(String name, String description, Set<Grant> grants);

	Optional<Role> find(UUID id);

	Optional<Role> findBuiltIn(Role.BuiltIn which);

	Page<Role> list(Pageable pageable);

	List<Role> findAll(Collection<UUID> ids);

	boolean nameInUse(String name, UUID except);

	/**
	 * Changes a role if it is still at {@code expectedVersion}, otherwise fails with an optimistic locking failure. A
	 * change to the grants alone also advances the version.
	 */
	Role update(UUID id, long expectedVersion, String name, String description, Set<Grant> grants);

	/** Marks the role deleted. The caller has checked that nobody holds it. */
	void softDelete(UUID id);

	/** Permanently removes roles deleted before the cutoff, with what they allowed. Returns how many. */
	int purgeDeletedBefore(java.time.Instant cutoff);

	/**
	 * Creates the two built-in roles if they are missing, gives the administrator role every grant in {@code administrator}
	 * it lacks, and offers the user role each grant in {@code userRole} whose action it has not been offered before.
	 */
	void ensureBuiltInRoles(Set<Grant> administrator, Set<Grant> userRole);

	/**
	 * Moves the rows of the contract-version-1 table {@code role_permission}, in every tenant, into grants: each stored
	 * name that {@code conversion} maps to a grant is written as that grant of the same role and removed. A name it does
	 * not map (an action no module declares now) is left where it is, so it is converted once its module is back.
	 * Returns how many rows were converted. A milestone 8 step; see {@code BuiltInRoles}.
	 */
	int convertLegacyPermissions(java.util.function.Function<String, Optional<Grant>> conversion);

	/** Locks the role's row until the transaction ends, so concurrent changes to who holds it take turns. */
	void lock(UUID roleId);

	Set<UUID> roleIdsOf(UUID userId);

	/** The role IDs of several users at once. A user who holds no role is absent from the map. */
	java.util.Map<UUID, Set<UUID>> roleIdsOfAll(Collection<UUID> userIds);

	/** The IDs of every user who holds the role. */
	List<UUID> holdersOf(UUID roleId);

	void assign(UUID userId, UUID roleId, UUID assignedBy);

	void unassign(UUID userId, UUID roleId);

	void unassignAll(UUID userId);

}
