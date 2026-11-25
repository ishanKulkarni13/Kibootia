package com.kibootia.backend.access.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.ResourceType;

/**
 * Every action and resource type the modules declare in code (ADR 0044, ADR 0054), collected at startup. The role editor
 * lists from it and the decision point refuses actions that are not in it, so a typo in {@code @RequiresPermission} is
 * found at startup or on first use instead of silently denying forever.
 *
 * <p>Startup fails on a malformed or duplicate name, an action on a resource type nobody declared, a user-role reach the
 * type does not offer, and a relation that is not a valid name.
 */
public final class PermissionRegistry {

	private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]*\\.[a-z][a-z0-9]*(-[a-z0-9]+)*\\.[a-z][a-z0-9]*(-[a-z0-9]+)*");

	private static final Pattern TYPE = Pattern.compile("[a-z][a-z0-9]*\\.[a-z][a-z0-9]*(-[a-z0-9]+)*");

	private final Map<String, Permission> byName = new LinkedHashMap<>();

	private final Map<String, ResourceType> types = new LinkedHashMap<>();

	public PermissionRegistry(Collection<Permission> permissions) {
		this(permissions, List.of());
	}

	public PermissionRegistry(Collection<Permission> permissions, Collection<ResourceType> resourceTypes) {
		List<String> problems = new ArrayList<>();
		for (ResourceType type : resourceTypes) {
			if (!TYPE.matcher(type.name()).matches()) {
				problems.add("resource type '" + type.name() + "' is not named <module>.<resource>");
			}
			for (String relation : type.relations()) {
				try {
					Reach.relation(relation);
				}
				catch (IllegalArgumentException ex) {
					problems.add("resource type '" + type.name() + "' declares the invalid relation '" + relation + "'");
				}
			}
			if (this.types.putIfAbsent(type.name(), type) != null) {
				problems.add("resource type '" + type.name() + "' is declared more than once");
			}
		}
		Set<String> duplicates = new LinkedHashSet<>();
		for (Permission permission : permissions) {
			if (!NAME.matcher(permission.name()).matches()) {
				throw new IllegalStateException("Permission '" + permission.name()
						+ "' is not named <module>.<resource>.<action> in lower-case words joined by hyphens");
			}
			if (this.byName.putIfAbsent(permission.name(), permission) != null) {
				duplicates.add(permission.name());
			}
		}
		if (!duplicates.isEmpty()) {
			throw new IllegalStateException("Permissions declared more than once: " + duplicates);
		}
		for (Permission permission : this.byName.values()) {
			if (permission.isOnRecord() && !this.types.containsKey(permission.resourceType())) {
				problems.add("'" + permission.name() + "' acts on the resource type '" + permission.resourceType()
						+ "', which no module declares");
			}
			else if (permission.userRoleReach() != null && !reachesOf(permission).contains(permission.userRoleReach())) {
				problems.add("'" + permission.name() + "' offers the user role the reach '"
						+ permission.userRoleReach().code() + "', which its resource type does not have");
			}
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Invalid authorization declarations: " + problems);
		}
	}

	public Collection<Permission> all() {
		return Collections.unmodifiableCollection(this.byName.values());
	}

	/** The actions a role can be given: every declared one except those only the system performs. */
	public Collection<Permission> grantable() {
		return this.byName.values().stream().filter(Permission::isGrantable).toList();
	}

	public Collection<ResourceType> resourceTypes() {
		return Collections.unmodifiableCollection(this.types.values());
	}

	public boolean isRegistered(String name) {
		return this.byName.containsKey(name);
	}

	/** Whether the action is declared and a role can be given it; false for one only the system performs. */
	public boolean isGrantable(String name) {
		Permission permission = this.byName.get(name);
		return permission != null && permission.isGrantable();
	}

	public Optional<Permission> find(String name) {
		return Optional.ofNullable(this.byName.get(name));
	}

	/** The grants the built-in {@code user} role is offered, with the reach each action declares for it. */
	public Set<Grant> userRoleGrants() {
		Set<Grant> grants = new LinkedHashSet<>();
		this.byName.values()
			.stream()
			.filter(Permission::grantedToUserRole)
			.forEach((permission) -> grants.add(Grant.allow(permission.name(), permission.userRoleReach())));
		return Collections.unmodifiableSet(grants);
	}

	/**
	 * The grants of the built-in administrator role: every action a role can be given, for every record (ADR 0054). Not the
	 * actions only the system performs: nobody holds those, the administrator included.
	 */
	public Set<Grant> administratorGrants() {
		Set<Grant> grants = new LinkedHashSet<>();
		grantable().forEach((permission) -> grants.add(Grant.allow(permission.name(), Reach.ALL)));
		return Collections.unmodifiableSet(grants);
	}

	/** The reaches a grant of the action can have: every record, plus each relation of its resource type. */
	public List<Reach> reachesOf(String action) {
		Permission permission = this.byName.get(action);
		return (permission == null) ? List.of() : reachesOf(permission);
	}

	private List<Reach> reachesOf(Permission permission) {
		List<Reach> reaches = new ArrayList<>();
		reaches.add(Reach.ALL);
		if (permission.isOnRecord()) {
			ResourceType type = this.types.get(permission.resourceType());
			if (type != null) {
				type.relations().stream().sorted().forEach((relation) -> reaches.add(Reach.relation(relation)));
			}
		}
		return reaches;
	}

	/**
	 * Whether the grant fits the declarations: a declared action that a role can be given, a reach its resource type
	 * offers, and, for a deny, the reach {@code all} (a deny that removes only some records would need a filter that
	 * excludes, which lists cannot apply yet).
	 */
	public boolean isValid(Grant grant) {
		Permission permission = this.byName.get(grant.action());
		if (permission == null || !permission.isGrantable() || !reachesOf(permission).contains(grant.reach())) {
			return false;
		}
		return grant.allows() || grant.reach() instanceof Reach.All;
	}

	/** Whether the action is checked with a record and can be granted for only some records. */
	public boolean isOnRecord(String name) {
		Permission permission = this.byName.get(name);
		return permission != null && permission.isOnRecord();
	}

	/** Whether a caller who has not verified their email address may use the permission (ADR 0041). */
	public boolean isAllowedBeforeVerification(String name) {
		Permission permission = this.byName.get(name);
		return permission != null && permission.allowedBeforeVerification();
	}

	/** Whether allowed uses of the permission are recorded too, not only refusals (ADR 0055, point 6). */
	public boolean isSensitive(String name) {
		Permission permission = this.byName.get(name);
		return permission != null && permission.sensitive();
	}

}
