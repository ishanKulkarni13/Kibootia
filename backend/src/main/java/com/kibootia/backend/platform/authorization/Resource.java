package com.kibootia.backend.platform.authorization;

/**
 * A record that can be checked with {@link Authorization#require(String, Resource)}. It describes itself to the
 * decision point; replaces the marker interfaces {@code Owned} and {@code TenantBound} of contract version 1.
 */
public interface Resource {

	ResourceRef resourceRef();

}
