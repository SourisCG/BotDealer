/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.logging;

import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import souris.botdealer.security.TokenPatterns;

/**
 * Logback converter ({@code %safeEx}) that renders the stack trace with token-shaped
 * text redacted, because a wrapped exception message can carry a secret too.
 *
 * <p>Renders as an empty string when the event has no throwable, so it can sit directly
 * after {@code %safeMsg} in the pattern.</p>
 */
public class RedactingThrowableConverter extends ThrowableHandlingConverter {

	@Override
	public String convert(ILoggingEvent event) {
		IThrowableProxy throwable = event.getThrowableProxy();
		if (throwable == null) {
			return "";
		}
		return System.lineSeparator() + TokenPatterns.redact(ThrowableProxyUtil.asString(throwable));
	}
}
