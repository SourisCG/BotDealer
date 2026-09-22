/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import souris.botdealer.security.TokenPatterns;

/**
 * Logback converter ({@code %safeMsg}) that redacts token-shaped text from the log
 * message before it reaches any appender.
 *
 * <p>Implemented as a plain {@link ClassicConverter} on purpose. The composite form
 * {@code %mask(%msg%ex)} looks tidier but swallows the remainder of the pattern in
 * Logback 1.5, which silently removed every newline from the log files.</p>
 *
 * <p>Patterns live in {@link TokenPatterns} so they never drift from the ones used by
 * the token validator and the UI masker.</p>
 */
public class RedactingMessageConverter extends ClassicConverter {

	@Override
	public String convert(ILoggingEvent event) {
		return TokenPatterns.redact(event.getFormattedMessage());
	}
}
