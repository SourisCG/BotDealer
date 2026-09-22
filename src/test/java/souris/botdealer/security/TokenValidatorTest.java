/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the token checker against a local stub of Discord's API, so tests never
 * touch the network and never need a real token.
 */
class TokenValidatorTest {

	private static final String TOKEN =
		String.join(".", "MTE5ODc2NTE5OTk5OTk5OTk5", "GxXxXx", "abcdefghijklmnopqrstuvwxyz123456");

	private static final String IDENTITY_JSON = """
		{"id":"119876519999999999","username":"botdealer","global_name":"BotDealer",
		 "avatar":"a_1234567890abcdef","bot":true}
		""";

	private HttpServer server;
	private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
	}

	private TokenValidator validatorReturning(int status, String body) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/users/@me", exchange -> {
			receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			byte[] payload = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, payload.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(payload);
			}
		});
		server.start();
		URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/users/@me");
		return new TokenValidator(new ObjectMapper(), endpoint);
	}

	@Test
	void acceptsAValidTokenAndReadsTheIdentity() throws Exception {
		TokenValidator validator = validatorReturning(200, IDENTITY_JSON);

		TokenValidation result = validator.validate(TOKEN);

		assertTrue(result.isValid());
		assertEquals(TokenValidation.Status.VALID, result.status());
		BotIdentity identity = result.identity();
		assertEquals("botdealer", identity.username());
		assertEquals("BotDealer", identity.displayName());
		assertTrue(identity.bot());
		assertTrue(identity.avatarUrl().startsWith("https://cdn.discordapp.com/avatars/"));
		assertTrue(identity.avatarUrl().endsWith(".gif?size=128"), "animated avatars must use .gif");
	}

	@Test
	void sendsTheTokenInTheAuthorizationHeaderOnly() throws Exception {
		TokenValidator validator = validatorReturning(200, IDENTITY_JSON);

		validator.validate(TOKEN);

		assertEquals("Bot " + TOKEN, receivedAuthorization.get());
	}

	@Test
	void reportsInvalidTokens() throws Exception {
		TokenValidation result = validatorReturning(401, "{\"message\":\"401: Unauthorized\"}").validate(TOKEN);

		assertFalse(result.isValid());
		assertEquals(TokenValidation.Status.INVALID, result.status());
		assertEquals("wizard.token.result.invalid", result.messageKey());
	}

	@Test
	void reportsRateLimiting() throws Exception {
		TokenValidation result = validatorReturning(429, "{}").validate(TOKEN);

		assertEquals(TokenValidation.Status.RATE_LIMITED, result.status());
	}

	@Test
	void reportsUnexpectedHttpStatusWithoutLeakingTheToken() throws Exception {
		TokenValidation result = validatorReturning(500, "boom").validate(TOKEN);

		assertEquals(TokenValidation.Status.HTTP_ERROR, result.status());
		assertEquals("500", result.detail());
		assertFalse(result.toString().contains(TOKEN), "the token must never appear in a result");
	}

	@Test
	void rejectsMalformedInputWithoutCallingDiscord() {
		TokenValidation result = new TokenValidator(new ObjectMapper(), URI.create("http://127.0.0.1:1/users/@me"))
			.validate("definitely-not-a-token");

		assertEquals(TokenValidation.Status.MALFORMED, result.status());
		assertEquals("wizard.token.result.malformed", result.messageKey());
	}

	@Test
	void reportsNetworkFailures() {
		TokenValidation result = new TokenValidator(new ObjectMapper(), URI.create("http://127.0.0.1:1/users/@me"))
			.validate(TOKEN);

		assertEquals(TokenValidation.Status.NETWORK_ERROR, result.status());
		assertEquals("wizard.token.result.network", result.messageKey());
	}
}
