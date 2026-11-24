package com.kibootia.backend.access.application;

import java.util.Set;

import com.kibootia.backend.platform.authorization.Grant;

/**
 * What an administrator supplies to change a role, with the version they started from so that an edit made meanwhile by
 * someone else is not overwritten.
 */
public record RoleChanges(String name, String description, Set<Grant> grants, long expectedVersion) {

}
