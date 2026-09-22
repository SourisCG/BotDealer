/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import souris.botdealer.service.betting.BetValidationException;

/**
 * Normalizes and vets what the user typed before it reaches yt-dlp.
 *
 * <p>Two jobs: turn a plain search phrase into a {@code ytsearch1:} target, and refuse
 * anything that is not an {@code http(s)} URL on an allowed host. Rejecting other
 * schemes matters because yt-dlp happily reads {@code file://} and could otherwise be
 * pointed at the local filesystem.</p>
 */
public final class YtDlpTargets {

	/** Hosts yt-dlp may be pointed at for this feature. */
	private static final List<String> ALLOWED_HOSTS = List.of(
		"youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
		"youtu.be", "www.youtu.be");

	private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}]");

	private YtDlpTargets() {
	}

	/**
	 * @param query a URL or a search phrase
	 * @return a target safe to append after {@code --}
	 * @throws BetValidationException when the input is unusable or points elsewhere
	 */
	public static String normalize(String query) {
		if (query == null || query.isBlank()) {
			throw new BetValidationException("music.error.emptyQuery");
		}
		String trimmed = query.strip();
		if (CONTROL_CHARACTERS.matcher(trimmed).find()) {
			throw new BetValidationException("music.error.invalidQuery");
		}
		if (trimmed.length() > 500) {
			throw new BetValidationException("music.error.queryTooLong");
		}

		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			requireAllowedHost(trimmed);
			return trimmed;
		}
		// Anything else is a search phrase, never a flag or a path.
		if (trimmed.startsWith("-") || trimmed.startsWith("/") || lower.contains(":")) {
			throw new BetValidationException("music.error.invalidQuery");
		}
		return "ytsearch1:" + trimmed;
	}

	private static void requireAllowedHost(String url) {
		try {
			java.net.URI uri = java.net.URI.create(url);
			String host = uri.getHost();
			if (host == null || ALLOWED_HOSTS.stream().noneMatch(allowed -> allowed.equalsIgnoreCase(host))) {
				throw new BetValidationException("music.error.unsupportedHost");
			}
		} catch (IllegalArgumentException e) {
			throw new BetValidationException("music.error.invalidQuery");
		}
	}

	/** True when the text looks like a URL rather than a search phrase. */
	public static boolean looksLikeUrl(String query) {
		if (query == null) {
			return false;
		}
		String lower = query.strip().toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	/** Best-effort YouTube video id, used to name cache files deterministically. */
	public static Optional<String> videoId(String url) {
		try {
			java.net.URI uri = java.net.URI.create(url);
			String v = null;
			if (uri.getQuery() != null) {
				for (String pair : uri.getQuery().split("&")) {
					String[] parts = pair.split("=", 2);
					if (parts.length == 2 && "v".equals(parts[0])) {
						v = parts[1];
					}
				}
			}
			if (v == null && uri.getPath() != null && uri.getHost() != null
					&& uri.getHost().toLowerCase(Locale.ROOT).endsWith("youtu.be")) {
				v = uri.getPath().replace("/", "");
			}
			if (v != null && v.matches("[A-Za-z0-9_-]{6,20}")) {
				return Optional.of(v);
			}
		} catch (IllegalArgumentException ignored) {
			// not a URL we can parse
		}
		return Optional.empty();
	}
}
