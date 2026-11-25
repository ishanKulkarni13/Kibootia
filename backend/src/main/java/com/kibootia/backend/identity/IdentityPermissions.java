package com.kibootia.backend.identity;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;

/**
 * The permissions of the identity module (ADR 0044, ADR 0041). None is held by the built-in {@code user} role: a new
 * user sees no one else, because with open sign-up any stranger could otherwise browse the user directory. Actions on
 * one's own profile need no permission; ownership allows them.
 */
@Component
public class IdentityPermissions implements PermissionProvider {

	/** See other users: the list, their profiles and their avatars (ADR 0041). */
	public static final String VIEW_PROFILES = "identity.profiles.view";

	/** Invite someone by email and send the invitation again. */
	public static final String INVITE_USERS = "identity.users.invite";

	/** Disable and enable accounts. */
	public static final String DISABLE_USERS = "identity.users.disable";

	/** Delete accounts. A deleted account can be restored until it is erased. */
	public static final String DELETE_USERS = "identity.users.delete";

	/** Bring a deleted account back. */
	public static final String RESTORE_USERS = "identity.users.restore";

	/** Erase a deleted account for good, on a privacy request. */
	public static final String ERASE_USERS = "identity.users.erase";

	/** Start erasing the accounts whose retention period has passed. Only the retention job does it. */
	public static final String PURGE_USERS = "identity.users.purge";

	@Override
	public List<Permission> permissions() {
		return List.of(Permission.onCollection(VIEW_PROFILES, "See other users and their profiles"),
				Permission.onCollection(INVITE_USERS, "Invite users by email"),
				Permission.onCollection(DISABLE_USERS, "Disable and enable accounts"),
				Permission.onCollection(DELETE_USERS, "Delete accounts").asSensitive(),
				Permission.onCollection(RESTORE_USERS, "Restore deleted accounts"),
				Permission.onCollection(ERASE_USERS, "Erase deleted accounts for good").asSensitive(),
				Permission.onCollection(PURGE_USERS, "Start erasing accounts whose retention period has passed")
					.asSensitive()
					.forSystemOnly());
	}

}
