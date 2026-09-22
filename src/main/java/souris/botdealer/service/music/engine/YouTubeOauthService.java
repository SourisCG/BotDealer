/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.io.IOException;
import java.util.Optional;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.http.YoutubeOauth2Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;
import souris.botdealer.service.music.DirectLoaderImpl;

/**
 * Links a YouTube account using Google's official device-code flow.
 *
 * <p>Why link at all: YouTube increasingly answers unauthenticated requests with "sign
 * in to confirm you're not a bot". Linking makes the requests look like a normal signed-in
 * user, and the library documents that OAuth removes the need for a PO token.</p>
 *
 * <p>The refresh token goes straight into the OS keychain; nothing is written to the
 * database and the token is never logged. The flow is the official one — the user types a
 * code on Google's page and BotDealer only ever sees the resulting refresh token.</p>
 *
 * <p>The library itself warns to use a throwaway account, and the UI repeats that.</p>
 */
@Service
public class YouTubeOauthService {

	private static final Logger log = LoggerFactory.getLogger(YouTubeOauthService.class);

	/** A code the user types on Google's page, plus the polling interval Google asks for. */
	public record DeviceCode(String verificationUrl, String userCode, String deviceCode, int intervalSeconds) {
	}

	/** One polling attempt. */
	public record PollResult(Status status, String refreshToken) {

		public enum Status {
			PENDING,
			READY,
			DENIED,
			EXPIRED,
			ERROR
		}
	}

	private final ObjectProvider<DirectLoaderImpl> loader;
	private final SecretService secrets;

	public YouTubeOauthService(ObjectProvider<DirectLoaderImpl> loader, SecretService secrets) {
		this.loader = loader;
		this.secrets = secrets;
	}

	/** True when a refresh token is stored. */
	public boolean isLinked() {
		return secrets.isSet(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN);
	}

	/** True when the flow can run at all (the YouTube source is registered). */
	public boolean isAvailable() {
		return handler().isPresent();
	}

	/** Asks Google for a code the user has to enter. */
	public Optional<DeviceCode> requestDeviceCode() {
		Optional<YoutubeOauth2Handler> handler = handler();
		if (handler.isEmpty()) {
			return Optional.empty();
		}
		try {
			JsonBrowser response = handler.get().fetchDeviceCode();
			String url = response.get("verification_url").textOrDefault("");
			String userCode = response.get("user_code").textOrDefault("");
			String deviceCode = response.get("device_code").textOrDefault("");
			int interval = parseInt(response.get("interval").textOrDefault("5"), 5);
			if (url.isBlank() || userCode.isBlank() || deviceCode.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(new DeviceCode(url, userCode, deviceCode, Math.max(2, interval)));
		} catch (Exception e) {
			log.warn("Could not start the YouTube account linking flow: {}", e.toString());
			return Optional.empty();
		}
	}

	/** One poll; the caller repeats it every {@link DeviceCode#intervalSeconds()}. */
	public PollResult poll(DeviceCode code) {
		Optional<YoutubeOauth2Handler> handler = handler();
		if (handler.isEmpty()) {
			return new PollResult(PollResult.Status.ERROR, "");
		}
		try {
			JsonBrowser response = handler.get().fetchRefreshToken(code.deviceCode());
			String error = response.get("error").textOrDefault("");
			switch (error) {
				case "authorization_pending":
					return new PollResult(PollResult.Status.PENDING, "");
				case "access_denied":
					return new PollResult(PollResult.Status.DENIED, "");
				case "expired_token":
					return new PollResult(PollResult.Status.EXPIRED, "");
				case "":
					break;
				default:
					log.debug("Unexpected OAuth error: {}", error);
					return new PollResult(PollResult.Status.ERROR, "");
			}
			String token = response.get("refresh_token").textOrDefault("");
			return token.isBlank()
				? new PollResult(PollResult.Status.PENDING, "")
				: new PollResult(PollResult.Status.READY, token);
		} catch (IOException e) {
			return new PollResult(PollResult.Status.PENDING, "");
		} catch (Exception e) {
			log.warn("YouTube account linking failed: {}", e.toString());
			return new PollResult(PollResult.Status.ERROR, "");
		}
	}

	/** Stores the refresh token in the keychain and activates it immediately. */
	public void link(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new IllegalArgumentException("Refusing to store an empty refresh token");
		}
		secrets.set(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN, refreshToken);
		handler().ifPresent(handler -> handler.setRefreshToken(refreshToken.strip(), true));
		log.info("YouTube account linked; the refresh token is in the credential store");
	}

	/** Forgets the account; YouTube will be used anonymously again. */
	public void disconnect() {
		secrets.remove(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN);
		handler().ifPresent(handler -> handler.setRefreshToken(null, true));
		log.info("YouTube account unlinked");
	}

	private Optional<YoutubeOauth2Handler> handler() {
		DirectLoaderImpl direct = loader.getIfAvailable();
		if (direct == null) {
			return Optional.empty();
		}
		return direct.youtubeSource()
			.map(YoutubeAudioSourceManager::getOauth2Handler)
			.filter(handler -> handler != null);
	}

	private static int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value.strip());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
