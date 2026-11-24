/**
 * Notes module. See kibootia-docs/engineering/conventions/package-structure.md.
 */
@ApplicationModule(displayName = "Notes", allowedDependencies = { "identity", "audit", "platform :: *" })
package com.kibootia.backend.notes;

import org.springframework.modulith.ApplicationModule;
