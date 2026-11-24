package com.kibootia.backend.platform.events;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.modulith.events.core.EventSerializer;

import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Events that are already queued must stay readable after a deployment (ADR 0059). Each event type of the application has
 * a golden file in {@code src/test/resources/events}: the JSON an earlier version wrote. The current code reads it with
 * the serializer the registry uses, and every component of the event must come out filled. Renaming or retyping a
 * component of an event record makes this test fail, instead of failing the delivery of events that were already queued.
 *
 * <p>When an event type is added, add its golden file, named after the class. When a component is added, add it to the
 * golden file as well, and keep the old file's other values: a component that an older event lacks must be safe when
 * absent (see {@link DomainEvent}).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventSchemaCompatibilityIT {

	@Autowired
	EventSerializer serializer;

	@Test
	@DisplayName("every event type of the application has a golden file, and the current code reads it completely")
	void everyEventTypeReadsItsGoldenFile() throws IOException {
		List<Class<?>> types = eventTypes();
		assertThat(types).as("the event types of the application").isNotEmpty();

		for (Class<?> type : types) {
			String golden = golden(type.getName());
			Object event = this.serializer.deserialize(golden, type);

			for (RecordComponent component : type.getRecordComponents()) {
				Object value = invoke(event, component);
				assertThat(value).as("%s.%s read from its golden file", type.getSimpleName(), component.getName())
					.isNotNull();
			}
			EventContext context = ((DomainEvent) event).context();
			assertThat(context.eventId()).as(type.getSimpleName() + " event ID").isNotNull();
			assertThat(context.tenantId()).as(type.getSimpleName() + " tenant").isEqualTo(TenantContext.DEFAULT_TENANT);
			assertThat(context.occurredAt()).as(type.getSimpleName() + " time").isNotNull();
			assertThat(context.schemaVersion()).as(type.getSimpleName() + " schema version")
				.isEqualTo(EventContext.CURRENT_VERSION);
		}
	}

	@Test
	@DisplayName("an event queued before versions existed is still read, and its version is 0")
	void anEventWithoutAVersionIsStillRead() {
		String queuedBefore = """
				{"userId": "11111111-1111-7111-8111-111111111111",
				 "context": {"eventId": "019a8d3e-7c41-7c4d-9b1e-2f6a4c8d0e11",
				             "tenantId": "00000000-0000-7000-8000-000000000001",
				             "traceparent": null, "occurredAt": "2026-11-09T08:00:00Z"}}
				""";

		com.kibootia.backend.identity.UserErased event = this.serializer.deserialize(queuedBefore,
				com.kibootia.backend.identity.UserErased.class);

		assertThat(event.userId()).isEqualTo(UUID.fromString("11111111-1111-7111-8111-111111111111"));
		assertThat(event.context().schemaVersion()).isZero();
		assertThat(event.context().tenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
	}

	@Test
	@DisplayName("what the registry writes today is what the golden files say it writes")
	void todaysSerializationKeepsTheContextShape() {
		EventContext context = new EventContext(UUID.fromString("019a8d3e-7c41-7c4d-9b1e-2f6a4c8d0e11"),
				TenantContext.DEFAULT_TENANT, null, java.time.Instant.parse("2026-11-25T10:15:30.123456Z"),
				EventContext.CURRENT_VERSION);

		String written = this.serializer.serialize(new com.kibootia.backend.identity.UserErased(
				UUID.fromString("11111111-1111-7111-8111-111111111111"), context)).toString();

		assertThat(written).contains("\"schemaVersion\":1", "\"tenantId\":\"00000000-0000-7000-8000-000000000001\"",
				"\"userId\":\"11111111-1111-7111-8111-111111111111\"", "\"occurredAt\":\"2026-11-25T10:15:30.123456Z\"");
	}

	private static List<Class<?>> eventTypes() {
		Iterable<JavaClass> classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("com.kibootia.backend");
		return StreamSupport.stream(classes.spliterator(), false)
			.filter((type) -> !type.isInterface() && type.isAssignableTo(DomainEvent.class))
			.<Class<?>>map(JavaClass::reflect)
			.toList();
	}

	private static String golden(String className) throws IOException {
		ClassPathResource resource = new ClassPathResource("events/" + className + ".json");
		assertThat(resource.exists()).as("a golden file events/%s.json for the event type %s", className, className)
			.isTrue();
		return resource.getContentAsString(StandardCharsets.UTF_8);
	}

	private static Object invoke(Object event, RecordComponent component) {
		try {
			return component.getAccessor().invoke(event);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
