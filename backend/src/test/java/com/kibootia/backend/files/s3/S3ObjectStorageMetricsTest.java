package com.kibootia.backend.files.s3;

import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.files.application.FilesProperties;
import com.kibootia.backend.files.application.ObjectStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A failed call to the storage server is counted by what was being done (ADR 0061). Nothing listens on the address, so no
 * container is needed; {@link S3ObjectStorageContractIT} covers the adapter against a real server.
 */
class S3ObjectStorageMetricsTest {

	private static FilesProperties unreachable() {
		return new FilesProperties(true, "http://localhost:1", "us-east-1", "metrics", "key", "secret", false, null, null,
				"0 0 4 * * *", 500);
	}

	@Test
	@DisplayName("each failed call adds one to the error count of what it was doing, and a call that was not made adds nothing")
	void failedCallsAreCountedByAction() {
		SimpleMeterRegistry meters = new SimpleMeterRegistry();
		try (S3ObjectStorage storage = new S3ObjectStorage(unreachable(), meters)) {
			assertThatThrownBy(() -> storage.put("k", new byte[] { 1 }, "image/png")).isInstanceOf(ObjectStorage.Unavailable.class);
			assertThatThrownBy(() -> storage.put("k", new byte[] { 1 }, "image/png")).isInstanceOf(ObjectStorage.Unavailable.class);
			assertThatThrownBy(() -> storage.delete("k")).isInstanceOf(ObjectStorage.Unavailable.class);
		}

		assertThat(meters.get(S3ObjectStorage.ERRORS).tag("action", "store an object").counter().count()).isEqualTo(2.0);
		assertThat(meters.get(S3ObjectStorage.ERRORS).tag("action", "delete an object").counter().count()).isEqualTo(1.0);
		assertThat(meters.find(S3ObjectStorage.ERRORS).tag("action", "list objects").counter()).isNull();
	}

	@Test
	@DisplayName("works without a metrics registry, as a test that builds the adapter by hand does")
	void worksWithoutARegistry() {
		try (S3ObjectStorage storage = new S3ObjectStorage(unreachable())) {
			assertThatThrownBy(() -> storage.deleteAll(List.of("a"))).isInstanceOf(ObjectStorage.Unavailable.class);
		}
	}

}
