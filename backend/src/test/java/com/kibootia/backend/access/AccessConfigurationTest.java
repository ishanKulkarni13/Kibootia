package com.kibootia.backend.access;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.ResourceType;
import com.kibootia.backend.platform.authorization.PermissionProvider;
import com.kibootia.backend.platform.authorization.RequiresPermission;

import static org.assertj.core.api.Assertions.assertThat;

class AccessConfigurationTest {

	static class Declares implements PermissionProvider {

		@Override
		public List<Permission> permissions() {
			return List.of(Permission.onCollection("probe.items.read", "Read items").forEveryUser(),
					Permission.onRecord("probe.items.update", "probe.item", "Change items").forEveryUser(Reach.owner()));
		}

		@Override
		public List<ResourceType> resourceTypes() {
			return List.of(ResourceType.of("probe.item", "An item", "owner"));
		}

	}

	static class GoodService {

		@RequiresPermission("probe.items.read")
		public void read() {
		}

	}

	/** An action on a record checked without the record: would pass for a holder of their own items only. */
	static class RecordActionService {

		@RequiresPermission("probe.items.update")
		public void update() {
		}

	}

	static class TypoService {

		@RequiresPermission("probe.items.raed")
		public void read() {
		}

	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(Declares.class)
		.withBean(PermissionSource.class, () -> (user) -> Set.of())
		.withUserConfiguration(AccessConfiguration.class);

	@Test
	@DisplayName("starts when every required permission is declared")
	void startsWithDeclaredPermissions() {
		this.runner.withBean(GoodService.class).run((context) -> assertThat(context).hasNotFailed());
	}

	@Test
	@DisplayName("fails at startup, naming the method, when @RequiresPermission has a typo")
	void failsOnUndeclaredPermission() {
		this.runner.withBean(TypoService.class).run((context) -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("TypoService.read")
				.hasStackTraceContaining("probe.items.raed");
		});
	}

	@Test
	@DisplayName("fails at startup when @RequiresPermission names an action on a record, which needs the record (ADR 0055)")
	void failsOnRecordActionWithoutRecord() {
		this.runner.withBean(RecordActionService.class).run((context) -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("RecordActionService.update")
				.hasStackTraceContaining("action on a record");
		});
	}

	@Test
	@DisplayName("fails at startup when an action acts on a resource type nobody declares")
	void failsOnUndeclaredResourceType() {
		new ApplicationContextRunner()
			.withBean(com.kibootia.backend.platform.authorization.PermissionProvider.class,
					() -> () -> List.of(Permission.onRecord("probe.items.update", "probe.ghost", "Change items")))
			.withBean(PermissionSource.class, () -> (user) -> Set.of())
			.withUserConfiguration(AccessConfiguration.class)
			.run((context) -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("probe.ghost");
			});
	}

	@Test
	@DisplayName("fails at startup when the user role is offered a reach the resource type does not have")
	void failsOnUnavailableReach() {
		new ApplicationContextRunner()
			.withBean(com.kibootia.backend.platform.authorization.PermissionProvider.class,
					() -> new com.kibootia.backend.platform.authorization.PermissionProvider() {

						@Override
						public List<Permission> permissions() {
							return List.of(Permission.onRecord("probe.items.update", "probe.item", "Change items")
								.forEveryUser(Reach.relation("reviewer")));
						}

						@Override
						public List<ResourceType> resourceTypes() {
							return List.of(ResourceType.of("probe.item", "An item", "owner"));
						}

					})
			.withBean(PermissionSource.class, () -> (user) -> Set.of())
			.withUserConfiguration(AccessConfiguration.class)
			.run((context) -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("relation:reviewer");
			});
	}

	@Test
	@DisplayName("fails at startup when two modules declare the same permission")
	void failsOnDuplicatePermission() {
		this.runner.withBean("again", Declares.class).run((context) -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("probe.items.read");
		});
	}

}
