/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Verifies a Discord bot token against {@code GET /users/@me} before it is ever
 * persisted.
 *
 * <p>Security notes: the token travels only in the {@code Authorization} header of a
 * TLS request, never in a URL or a log line; failures are reported with a short
 * non-sensitive reason.</p>
 */
@Component
public class TokenValidator {

	private static final Logger log = LoggerFactory.getLogger(TokenValidator.class);

	public static final URI DISCORD_ME = URI.create("https://discord.com/api/v10/users/@me");
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
	private static final String USER_AGENT = "BotDealer (https://github.com/SourisCG/BotDealer)";

	private final ObjectMapper mapper;
	private final URI endpoint;
	private final HttpClient http;

	@Autowired
	public TokenValidator(ObjectMapper mapper) {
		this(mapper, DISCORD_ME);
	}

	/** Test seam: point the validator at a local stub server. */
	TokenValidator(ObjectMapper mapper, URI endpoint) {
		this.mapper = mapper;
		this.endpoint = endpoint;
		this.http = HttpClient.newBuilder()
			.connectTimeout(REQUEST_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	}

	public TokenValidation validate(String token) {
		if (!TokenPatterns.looksLikeDiscordToken(token)) {
			return TokenValidation.malformed();
		}
		HttpRequest request = HttpRequest.newBuilder(endpoint)
			.timeout(REQUEST_TIMEOUT)
			.header("Authorization", "Bot " + token.strip())
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			return switch (response.statusCode()) {
				case 200 -> parseIdentity(response.body());
				case 401, 403 -> TokenValidation.invalid();
				case 429 -> TokenValidation.rateLimited();
				default -> TokenValidation.httpError(response.statusCode());
			};
		} catch (IOException e) {
			log.warn("Discord token validation failed: network error ({})", e.getClass().getSimpleName());
			return TokenValidation.networkError(e.getClass().getSimpleName());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return TokenValidation.networkError("interrupted");
		}
	}

	private TokenValidation parseIdentity(String body) {
		try {
			JsonNode root = mapper.readTree(body);
			String id = root.path("id").asText("");
			if (id.isBlank()) {
				return TokenValidation.httpError(200);
			}
			String username = root.path("username").asText("");
			String globalName = root.path("global_name").isNull() ? "" : root.path("global_name").asText("");
			String avatar = root.path("avatar").isNull() ? "" : root.path("avatar").asText("");
			boolean bot = root.path("bot").asBoolean(false);
			return TokenValidation.valid(new BotIdentity(id, username, globalName, avatarUrl(id, avatar), bot));
		} catch (Exception e) {
			return TokenValidation.httpError(200);
		}
	}

	private static String avatarUrl(String userId, String avatarHash) {
		if (avatarHash != null && !avatarHash.isBlank()) {
			String extension = avatarHash.startsWith("a_") ? "gif" : "png";
			return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + "." + extension + "?size=128";
		}
		try {
			int index = (int) ((Long.parseLong(userId) >> 22) % 6);
			return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
		} catch (NumberFormatException e) {
			return "https://cdn.discordapp.com/embed/avatars/0.png";
		}
	}
}
