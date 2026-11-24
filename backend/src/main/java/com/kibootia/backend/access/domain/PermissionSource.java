package com.kibootia.backend.access.domain;

import java.util.Set;

import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Principal;

/**
 * Where a user's grants come from: the roles stored in the database, read by one indexed query per call so a change
 * applies on the next request on any instance (ADR 0044). Grants that no longer fit the declarations (an action a
 * removed module declared) are left out by the implementation.
 */
public interface PermissionSource {

	Set<Grant> grantsOf(Principal.User user);

}
