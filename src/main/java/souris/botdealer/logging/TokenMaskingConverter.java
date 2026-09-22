/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.logging;

import java.util.regex.Pattern;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

/**
 * Logback converter ({@code %mask(...)}) that redacts anything that looks like a
 * Discord bot token before it reaches any appender. Defense in depth: the token must
 * never be logged in the first place; this converter catches accidents.
 *
 * <p>Declared as a {@link CompositeConverter} because Logback parses the
 * {@code %word(subPattern)} syntax as a composite converter and passes the rendered
 * sub-pattern to {@link #transform}. Using it as {@code %mask(%msg%ex)} also scrubs
 * stack traces.</p>
 *
 * <p>Covered formats: classic bot tokens ({@code xxx.yyy.zzz}) and {@code mfa.*}
 * tokens. The original text is never echoed back.</p>
 */
public class TokenMaskingConverter extends CompositeConverter<ILoggingEvent> {

	private static final Pattern CLASSIC_TOKEN =
		Pattern.compile("(?i)\\b[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{20,}\\b");
	private static final Pattern MFA_TOKEN =
		Pattern.compile("(?i)\\bmfa\\.[A-Za-z0-9_\\-.]{20,}");

	private static final String REDACTED = "***REDACTED-SECRET***";

	@Override
	protected String transform(ILoggingEvent event, String in) {
		if (in == null || in.isEmpty()) {
			return "";
		}
		String masked = CLASSIC_TOKEN.matcher(in).replaceAll(REDACTED);
		return MFA_TOKEN.matcher(masked).replaceAll(REDACTED);
	}
}
