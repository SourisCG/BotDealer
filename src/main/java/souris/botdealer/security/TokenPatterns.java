/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonical patterns for Discord credentials.
 *
 * <p>Kept in one place so the log redactor, the client-side format check and the
 * UI masker never drift apart.</p>
 */
public final class TokenPatterns {

	/** Classic bot token: {@code base64ish.base64ish.longerBase64ish}. */
	public static final Pattern CLASSIC_TOKEN =
		Pattern.compile("(?i)\\b[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{20,}\\b");

	/** MFA-style token: {@code mfa.<secret>}. */
	public static final Pattern MFA_TOKEN =
		Pattern.compile("(?i)\\bmfa\\.[A-Za-z0-9_\\-.]{20,}");

	public static final String REDACTED = "***REDACTED-SECRET***";

	private TokenPatterns() {
	}

	/** Redacts every token-shaped substring of the given text. */
	public static String redact(String text) {
		if (text == null || text.isEmpty()) {
			return text == null ? "" : text;
		}
		String masked = CLASSIC_TOKEN.matcher(text).replaceAll(REDACTED);
		return MFA_TOKEN.matcher(masked).replaceAll(REDACTED);
	}

	/** Cheap client-side sanity check before spending a network round trip. */
	public static boolean looksLikeDiscordToken(String candidate) {
		if (candidate == null) {
			return false;
		}
		String trimmed = candidate.strip();
		return !trimmed.isEmpty()
			&& (CLASSIC_TOKEN.matcher(trimmed).matches() || MFA_TOKEN.matcher(trimmed).matches());
	}

	/** Locale-independent lower case, for comparing/validating text. */
	public static String normalize(String value) {
		return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
	}

	/**
	 * Safe representation for the UI: only the last four characters survive, so a
	 * user can tell stored tokens apart without the full value ever being rendered.
	 */
	public static String maskForDisplay(String secret) {
		if (secret == null || secret.isBlank()) {
			return "";
		}
		String trimmed = secret.strip();
		if (trimmed.length() <= 4) {
			return "\u2022\u2022\u2022\u2022";
		}
		return "\u2022\u2022\u2022\u2022" + trimmed.substring(trimmed.length() - 4);
	}
}
