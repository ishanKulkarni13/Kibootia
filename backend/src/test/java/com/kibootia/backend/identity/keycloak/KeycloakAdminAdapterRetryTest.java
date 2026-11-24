package com.kibootia.backend.identity.keycloak;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.identity.application.IdentityProperties;
import com.kibootia.backend.identity.application.IdentityProviderAdmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which Keycloak calls are repeated, and when (ADR 0058). A small HTTP server stands in for Keycloak and answers with the
 * statuses a test queues for each kind of call, so the tests need no container. The real adapter runs against a real
 * Keycloak in {@link KeycloakAdminAdapterIT}.
 */
class KeycloakAdminAdapterRetryTest {

	private static final UUID ACCOUNT = UUID.fromString("11111111-1111-7111-8111-111111111111");

	private HttpServer server;

	/** Statuses to answer with, in order, per kind of call; an empty queue answers 200 (201 for creating). */
	private final Deque<Integer> reads = new ArrayDeque<>();

	private final Deque<Integer> deletes = new ArrayDeque<>();

	private final Deque<Integer> creates = new ArrayDeque<>();

	private final Deque<Integer> emails = new ArrayDeque<>();

	private final AtomicInteger readCalls = new AtomicInteger();

	private final AtomicInteger deleteCalls = new AtomicInteger();

	private final AtomicInteger createCalls = new AtomicInteger();

	private final AtomicInteger emailCalls = new AtomicInteger();

	@BeforeEach
	void startKeycloak() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		this.server.createContext("/", this::answer);
		this.server.start();
	}

	@AfterEach
	void stopKeycloak() {
		this.server.stop(0);
	}

	private void answer(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String method = exchange.getRequestMethod();
		exchange.getRequestBody().readAllBytes();
		if (path.endsWith("/protocol/openid-connect/token")) {
			respond(exchange, 200, "{\"access_token\":\"token\",\"expires_in\":300}");
		}
		else if (path.endsWith("/execute-actions-email") || path.endsWith("/send-verify-email")) {
			this.emailCalls.incrementAndGet();
			respond(exchange, next(this.emails, 204), "");
		}
		else if (method.equals("GET")) {
			this.readCalls.incrementAndGet();
			respond(exchange, next(this.reads, 200), "{\"enabled\":true,\"emailVerified\":true}");
		}
		else if (method.equals("DELETE")) {
			this.deleteCalls.incrementAndGet();
			respond(exchange, next(this.deletes, 204), "");
		}
		else if (method.equals("POST") && path.endsWith("/users")) {
			this.createCalls.incrementAndGet();
			int status = next(this.creates, 201);
			if (status == 201) {
				exchange.getResponseHeaders().add("Location", path + "/" + ACCOUNT);
			}
			respond(exchange, status, "");
		}
		else {
			respond(exchange, 404, "");
		}
	}

	private static int next(Deque<Integer> queue, int otherwise) {
		Integer status = queue.pollFirst();
		return (status != null) ? status : otherwise;
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, (bytes.length == 0 || status == 204) ? -1 : bytes.length);
		if (bytes.length > 0 && status != 204) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
		exchange.close();
	}

	private KeycloakAdminAdapter adapter(int retries) {
		IdentityProperties properties = new IdentityProperties(null, "http://localhost:3470", Duration.ofDays(1),
				new IdentityProperties.Keycloak("http://localhost:" + this.server.getAddress().getPort(), "kibootia",
						"kibootia-web", "kibootia-admin", "secret", Duration.ofSeconds(2), Duration.ofSeconds(5), retries,
						Duration.ofMillis(1)));
		return new KeycloakAdminAdapter(properties, Clock.systemUTC());
	}

	@Test
	@DisplayName("a lookup that meets a 503 twice is repeated and then succeeds")
	void aLookupIsRepeatedAfterTemporaryFailures() {
		this.reads.addAll(List.of(503, 503));

		assertThat(adapter(2).find(ACCOUNT)).isPresent();

		assertThat(this.readCalls.get()).as("two failures and the call that worked").isEqualTo(3);
	}

	@Test
	@DisplayName("a lookup gives up after the configured number of repeats and reports Keycloak as unavailable")
	void aLookupGivesUp() {
		this.reads.addAll(List.of(500, 502, 503, 503));

		assertThatThrownBy(() -> adapter(2).find(ACCOUNT)).isInstanceOf(IdentityProviderAdmin.Unavailable.class);

		assertThat(this.readCalls.get()).as("the first call and two repeats").isEqualTo(3);
	}

	@Test
	@DisplayName("a 404 is an answer, not a failure: the lookup is not repeated and finds nothing")
	void aNotFoundIsNotRepeated() {
		this.reads.add(404);

		assertThat(adapter(2).find(ACCOUNT)).isEmpty();

		assertThat(this.readCalls.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("deleting an account is repeated after a 500, and an account that is already gone is not an error")
	void aDeleteIsRepeated() {
		this.deletes.addAll(List.of(500));
		adapter(2).delete(ACCOUNT);
		assertThat(this.deleteCalls.get()).isEqualTo(2);

		this.deletes.add(404);
		adapter(2).delete(ACCOUNT);
		assertThat(this.deleteCalls.get()).as("the 404 was not repeated").isEqualTo(3);
	}

	@Test
	@DisplayName("creating an account and sending an email are never repeated: a repeat would make a second one")
	void creatingAndEmailingAreNotRepeated() {
		this.creates.addAll(List.of(503, 503));
		this.emails.addAll(List.of(503, 503));
		KeycloakAdminAdapter adapter = adapter(2);

		assertThatThrownBy(() -> adapter.createUser("someone@kibootia.test", true, List.of()))
			.isInstanceOf(IdentityProviderAdmin.Unavailable.class);
		assertThatThrownBy(() -> adapter.sendInvitation(ACCOUNT)).isInstanceOf(IdentityProviderAdmin.Unavailable.class);

		assertThat(this.createCalls.get()).isEqualTo(1);
		assertThat(this.emailCalls.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("with retries set to 0 nothing is repeated")
	void retryingCanBeTurnedOff() {
		this.reads.addAll(List.of(503, 503));

		assertThatThrownBy(() -> adapter(0).find(ACCOUNT)).isInstanceOf(IdentityProviderAdmin.Unavailable.class);

		assertThat(this.readCalls.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("a server that is not there is repeated and then reported as unavailable")
	void anUnreachableServerIsRepeated() {
		KeycloakAdminAdapter adapter = adapter(2);
		this.server.stop(0);

		assertThatThrownBy(() -> adapter.find(ACCOUNT)).isInstanceOf(IdentityProviderAdmin.Unavailable.class);
	}

}
