package com.kibootia.backend.access.application;

import java.util.Set;

import com.kibootia.backend.platform.authorization.Grant;

/** What an administrator supplies to create a role: its name, what it is for, and its grants (ADR 0054). */
public record NewRole(String name, String description, Set<Grant> grants) {

}
