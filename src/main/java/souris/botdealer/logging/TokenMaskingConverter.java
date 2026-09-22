/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import souris.botdealer.security.TokenPatterns;

/**
 * Logback converter ({@code %mask(...)}) that redacts anything that looks like a
 * Discord token before it reaches any appender. Defense in depth: the token must
 * never be logged in the first place; this converter catches accidents.
 *
 * <p>Declared as a {@link CompositeConverter} because Logback parses the
 * {@code %word(subPattern)} syntax as a composite converter and passes the rendered
 * sub-pattern to {@link #transform}. Using it as {@code %mask(%msg%ex)} also scrubs
 * stack traces.</p>
 *
 * <p>Patterns live in {@link TokenPatterns} so they never drift from the ones used
 * by the token validator and the UI masker.</p>
 */
public class TokenMaskingConverter extends CompositeConverter<ILoggingEvent> {

	@Override
	protected String transform(ILoggingEvent event, String in) {
		return TokenPatterns.redact(in);
	}
}
