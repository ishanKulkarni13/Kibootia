package com.kibootia.backend.platform.fixtures;

import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.authorization.ResourceType;
import com.kibootia.backend.platform.authorization.PermissionProvider;
import com.kibootia.backend.platform.authorization.RequiresPermission;

/**
 * A stand-in feature for the tests of the platform, access and identity modules: actions with the same shape as a
 * real feature's (a resource type with an owner, actions on records every user holds for their own, an action on the
 * collection that must be granted) and one protected endpoint pair. Those tests need some declared permissions and some protected URL to
 * work against; naming a real feature there would stop the application's tests from passing once that feature is
 * removed. Imported by {@code AdminApiTest}, so every integration test built on it has it.
 *
 * <p>The widgets do not exist as data: the endpoints only check the permission and answer. They are not part of the
 * application and never appear in {@code openapi.json}, which is generated from a context that does not import this.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(WidgetFixtures.WidgetsApi.class)
public class WidgetFixtures {

	public static final String CREATE = "widgets.widgets.create";

	public static final String VIEW = "widgets.widgets.view";


	public static final String UPDATE = "widgets.widgets.update";


	public static final String WIDGET = "widgets.widget";

	public static final String DELETE = "widgets.widgets.delete";


	public static final String RESTORE = "widgets.widgets.restore";

	@Bean
	PermissionProvider widgetPermissions() {
		return new PermissionProvider() {

			@Override
			public List<Permission> permissions() {
				return List.of(Permission.onCollection(CREATE, "Create widgets").forEveryUser(),
						Permission.onRecord(VIEW, WIDGET, "View widgets").forEveryUser(Reach.owner()),
						Permission.onRecord(UPDATE, WIDGET, "Change widgets").forEveryUser(Reach.owner()),
						Permission.onRecord(DELETE, WIDGET, "Delete widgets").forEveryUser(Reach.owner()),
						Permission.onCollection(RESTORE, "Restore deleted widgets"));
			}

			@Override
			public List<ResourceType> resourceTypes() {
				return List.of(ResourceType.of(WIDGET, "A widget", ResourceRef.OWNER));
			}

		};
	}

	/** Two endpoints that need a permission and nothing else, for tests that need a protected URL. */
	@RestController
	@RequestMapping("/api/v1/widgets")
	public static class WidgetsApi {

		private final Authorization authorization;

		WidgetsApi(Authorization authorization) {
			this.authorization = authorization;
		}

		@PostMapping
		@ResponseStatus(HttpStatus.CREATED)
		@RequiresPermission(CREATE)
		public String create() {
			return "created";
		}

		/** A list asks for its access filter: viewing is an action on a record, so it cannot be a method-level check. */
		@GetMapping
		public String list() {
			this.authorization.requireFilter(VIEW);
			return "listed";
		}

	}

}
