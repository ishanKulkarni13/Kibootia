/**
 * Identity module. See kibootia-docs/engineering/conventions/package-structure.md.
 */
@ApplicationModule(displayName = "Identity", allowedDependencies = { "access", "audit", "platform :: *" })
package com.kibootia.backend.identity;

import org.springframework.modulith.ApplicationModule;
