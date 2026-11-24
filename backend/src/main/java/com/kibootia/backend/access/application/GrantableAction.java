package com.kibootia.backend.access.application;

import java.util.List;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Reach;

/**
 * An action as the role editor offers it: the declaration and the reaches a grant of it can have, widest first.
 *
 * @param reaches {@code all}, then each relation of the action's resource type
 */
public record GrantableAction(Permission permission, List<Reach> reaches) {

}
