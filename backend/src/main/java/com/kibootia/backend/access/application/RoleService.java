package com.kibootia.backend.access.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.access.AccessPermissions;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;

/**
 * The use cases for roles (ADR 0025, ADR 0044, ADR 0054). A role is a set of grants. Every change is audited in its own
 * transaction, and two rules stop role editing from becoming a way to escalate privilege: an editor can only give a role
 * grants they hold themselves, at least as far as they reach, and the administrator role cannot be edited at all.
 */
@Service
public class RoleService {

	public static final String NOT_FOUND = "access.role_not_found";

	public static final String NAME_IN_USE = "access.role_name_in_use";

	public static final String PROTECTED = "access.role_protected";

	public static final String IN_USE = "access.role_in_use";

	/** No '@', so a role name written into the audit log can never look like an email address (ADR 0046). */
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._-]{0,78}[A-Za-z0-9]|[A-Za-z0-9]");

	/** The audit log keeps a value to 200 characters; a long list of permissions is summarised as a count. */
	private static final int AUDIT_VALUE_LIMIT = 200;

	private final RoleStore roles;

	private final PermissionRegistry registry;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final RateLimiter rateLimiter;

	private final AccessProperties properties;

	RoleService(RoleStore roles, PermissionRegistry registry, Authorization authorization, AuditLog auditLog,
			RateLimiter rateLimiter, AccessProperties properties) {
		this.roles = roles;
		this.registry = registry;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.rateLimiter = rateLimiter;
		this.properties = properties;
	}

	/** Every action the modules declare, with the reaches a grant of it can have, for the role editor. */
	@RequiresPermission(AccessPermissions.VIEW_ROLES)
	public List<GrantableAction> permissions() {
		return this.registry.all()
			.stream()
			.map((permission) -> new GrantableAction(permission, this.registry.reachesOf(permission.name())))
			.toList();
	}

	/** Whether the role editor may offer deny grants (ADR 0054, rule 5). */
	@NoAuthorization("a setting of the application, the same for everyone")
	public boolean denyGrantsEnabled() {
		return this.properties.denyGrantsEnabled();
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AccessPermissions.VIEW_ROLES)
	public Page<Role> list(Pageable pageable) {
		return this.roles.list(pageable);
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AccessPermissions.VIEW_ROLES)
	public Role get(UUID id) {
		return this.roles.find(id).orElseThrow(RoleService::notFound);
	}

	@Transactional
	@RequiresPermission(AccessPermissions.MANAGE_ROLES)
	public Role create(NewRole role) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		String name = validName(role.name());
		Set<Grant> grants = validGrants(role.grants(), Set.of());
		Grantable.requireHeld(this.authorization, grants);
		if (this.roles.nameInUse(name, null)) {
			throw nameInUse();
		}
		Role created = this.roles.insert(name, role.description().strip(), grants);
		this.auditLog.record(AuditEntry.of("access.role.created", "role", created.id())
			.withChange("roleName", name)
			.withChange("grants", summarise(grants, Set.of())));
		return created;
	}

	@Transactional
	@RequiresPermission(AccessPermissions.MANAGE_ROLES)
	public Role update(UUID id, RoleChanges changes) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		Role current = this.roles.find(id).orElseThrow(RoleService::notFound);
		if (current.builtIn() == Role.BuiltIn.ADMIN) {
			throw protectedRole("The administrator role holds every permission, for every record, and cannot be edited.");
		}
		String name = validName(changes.name());
		if (current.builtIn() == Role.BuiltIn.USER && !name.equals(current.name())) {
			throw protectedRole("The user role cannot be renamed.");
		}
		Set<Grant> grants = validGrants(changes.grants(), current.grants());
		Set<Grant> added = difference(grants, current.grants());
		Set<Grant> removed = difference(current.grants(), grants);
		Grantable.requireHeld(this.authorization, added);
		if (!name.equals(current.name()) && this.roles.nameInUse(name, id)) {
			throw nameInUse();
		}
		Role updated = this.roles.update(id, changes.expectedVersion(), name, changes.description().strip(), grants);
		AuditEntry entry = AuditEntry.of("access.role.updated", "role", id).withChange("roleName", name);
		if (!added.isEmpty() || !removed.isEmpty()) {
			entry = entry.withChange("grants", summarise(added, removed));
		}
		this.auditLog.record(entry);
		return updated;
	}

	@Transactional
	@RequiresPermission(AccessPermissions.MANAGE_ROLES)
	public void delete(UUID id) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		Role role = this.roles.find(id).orElseThrow(RoleService::notFound);
		if (role.isBuiltIn()) {
			throw protectedRole("The built-in roles cannot be deleted.");
		}
		if (role.assignedUsers() > 0) {
			throw ApiException.conflict(IN_USE, "Users still hold this role. Take it away from them first.");
		}
		this.roles.softDelete(id);
		this.auditLog.record(AuditEntry.of("access.role.deleted", "role", id).withChange("roleName", role.name()));
	}

	private String validName(String name) {
		String stripped = (name == null) ? "" : name.strip();
		if (!NAME.matcher(stripped).matches()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("name", "invalid_format",
							"must be 1 to 80 letters, digits, spaces, dots, dashes or underscores")));
		}
		return stripped;
	}

	/**
	 * The grants as asked for, each checked against the declarations: a declared action, a reach its resource type
	 * offers, and a deny only while deny grants are switched on. A deny the role already has may stay when they are off,
	 * because taking it away would widen access as a side effect of an unrelated edit.
	 */
	private Set<Grant> validGrants(Set<Grant> requested, Set<Grant> existing) {
		Set<Grant> grants = new LinkedHashSet<>();
		List<ApiFieldError> errors = new ArrayList<>();
		for (Grant grant : (requested == null) ? Set.<Grant>of() : requested) {
			if (!this.registry.isRegistered(grant.action())) {
				errors.add(new ApiFieldError("grants", "unknown_permission", "'" + grant.action() + "' is not a permission"));
			}
			else if (!this.registry.isValid(grant)) {
				errors.add(new ApiFieldError("grants", "invalid_reach",
						"'" + grant.action() + "' cannot be granted with the reach '" + grant.reach().code() + "'"));
			}
			else if (!grant.allows() && !this.properties.denyGrantsEnabled() && !existing.contains(grant)) {
				errors.add(new ApiFieldError("grants", "deny_grants_disabled", "Deny grants are switched off"));
			}
			else {
				grants.add(grant);
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
		return grants;
	}

	private static Set<Grant> difference(Set<Grant> from, Set<Grant> minus) {
		Set<Grant> result = new LinkedHashSet<>(from);
		result.removeAll(minus);
		return result;
	}

	/** {@code +a/all,+b/relation:owner,-c/all}, or a count when that would not fit in an audit value. */
	static String summarise(Set<Grant> added, Set<Grant> removed) {
		List<String> parts = new ArrayList<>();
		added.stream().map(Grant::describe).sorted().forEach((text) -> parts.add("+" + text));
		removed.stream().map(Grant::describe).sorted().forEach((text) -> parts.add("-" + text));
		String text = String.join(",", parts);
		if (text.length() <= AUDIT_VALUE_LIMIT) {
			return text.isEmpty() ? "none" : text;
		}
		return "+" + added.size() + " -" + removed.size() + " grants";
	}

	private static ApiException notFound() {
		return ApiException.notFound(NOT_FOUND, "There is no role with that ID.");
	}

	private static ApiException nameInUse() {
		return ApiException.conflict(NAME_IN_USE, "Another role already has this name.");
	}

	private static ApiException protectedRole(String message) {
		return ApiException.conflict(PROTECTED, message);
	}

}
