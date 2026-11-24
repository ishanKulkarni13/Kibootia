/**
 * Access module. See kibootia-docs/engineering/conventions/package-structure.md.
 */
@ApplicationModule(displayName = "Access", allowedDependencies = { "audit", "platform :: *" })
package com.kibootia.backend.access;

import org.springframework.modulith.ApplicationModule;
