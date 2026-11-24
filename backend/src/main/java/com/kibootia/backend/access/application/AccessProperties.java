package com.kibootia.backend.access.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the access module.
 *
 * @param denyGrantsEnabled whether roles may be given deny grants (ADR 0054, rule 5; milestone 8). Off by default: a
 *        deny is a powerful tool that most applications do not need. Switching it off does not ignore deny grants that
 *        already exist, which would widen access; it only stops new ones from being added ({@code kibootia.access.deny-grants-enabled})
 */
@ConfigurationProperties("kibootia.access")
public record AccessProperties(boolean denyGrantsEnabled) {

}
