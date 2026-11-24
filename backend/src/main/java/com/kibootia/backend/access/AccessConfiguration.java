package com.kibootia.backend.access;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.kibootia.backend.access.application.AccessProperties;
import com.kibootia.backend.access.domain.DefaultDecisionPoint;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;
import com.kibootia.backend.platform.authorization.PolicyDecisionPoint;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.authorization.ResourceRule;
import com.kibootia.backend.platform.authorization.ResourceType;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Wires the decision point (ADR 0044, ADR 0054): the actions and resource types every module declares, and the module
 * rules. A user's grants come from the stored roles ({@code StoredPermissionSource}, found by component scanning).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccessProperties.class)
class AccessConfiguration {

	@Bean
	PermissionRegistry permissionRegistry(ObjectProvider<PermissionProvider> providers) {
		List<Permission> declared = new ArrayList<>();
		List<ResourceType> types = new ArrayList<>();
		providers.orderedStream().forEach((provider) -> {
			declared.addAll(provider.permissions());
			types.addAll(provider.resourceTypes());
		});
		return new PermissionRegistry(declared, types);
	}

	@Bean
	PolicyDecisionPoint policyDecisionPoint(PermissionRegistry registry, PermissionSource source,
			ObjectProvider<ResourceRule> rules) {
		return new DefaultDecisionPoint(registry, source, rules.orderedStream().toList());
	}

	/**
	 * Every {@code @RequiresPermission} must name a declared action, and an action on the collection: an action on a
	 * record can be granted for only some records, so a check without the record would pass for a holder who may touch
	 * just their own (ADR 0055, point 5). Either mistake stops the application at startup.
	 */
	@Bean
	SmartInitializingSingleton requiredPermissionsAreDeclared(ApplicationContext context, PermissionRegistry registry) {
		return () -> {
			List<String> unknown = new ArrayList<>();
			List<String> onRecord = new ArrayList<>();
			for (String name : context.getBeanDefinitionNames()) {
				Class<?> type = context.getType(name, false);
				if (type == null) {
					continue;
				}
				ReflectionUtils.doWithMethods(ClassUtils.getUserClass(type), (Method method) -> {
					RequiresPermission required = AnnotatedElementUtils.findMergedAnnotation(method,
							RequiresPermission.class);
					if (required == null) {
						return;
					}
					String where = type.getSimpleName() + "." + method.getName() + " requires '" + required.value() + "'";
					if (!registry.isRegistered(required.value())) {
						unknown.add(where);
					}
					else if (registry.isOnRecord(required.value())) {
						onRecord.add(where);
					}
				});
			}
			if (!unknown.isEmpty()) {
				throw new IllegalStateException("@RequiresPermission names a permission nobody declared: " + unknown);
			}
			if (!onRecord.isEmpty()) {
				throw new IllegalStateException("@RequiresPermission names an action on a record, which must be checked "
						+ "with the record (Authorization.require or requireVisible): " + onRecord);
			}
		};
	}

}
