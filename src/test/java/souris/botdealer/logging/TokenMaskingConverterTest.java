/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * A Discord token must never survive the logging pipeline, even by accident.
 * The converter is a CompositeConverter, so tests exercise {@code transform(event, renderedSubPattern)}.
 */
class TokenMaskingConverterTest {

	private final TokenMaskingConverter converter = new TokenMaskingConverter();

	private static ILoggingEvent anyEvent() {
		return mock(ILoggingEvent.class);
	}

	@Test
	void masksClassicBotToken() {
		String token = String.join(".", "MTE5ODc2NTE5OTk5OTk5OTk5", "GxXxXx", "abcdefghijklmnopqrstuvwxyz123456");
		String out = converter.transform(anyEvent(), "login with " + token + " done");
		assertEquals("login with ***REDACTED-SECRET*** done", out);
		assertFalse(out.contains("GxXxXx"), "token fragment leaked into logs");
	}

	@Test
	void masksMfaToken() {
		assertEquals("key=***REDACTED-SECRET***",
			converter.transform(anyEvent(), "key=mfa." + "V0BDb00Kabcdefghijklmnopqrstuvwxyz0123456789"));
	}

	@Test
	void leavesNormalMessagesUntouched() {
		assertEquals("BotDealer UI started (locale=en)",
			converter.transform(anyEvent(), "BotDealer UI started (locale=en)"));
	}

	@Test
	void handlesNullAndEmpty() {
		assertEquals("", converter.transform(anyEvent(), null));
		assertEquals("", converter.transform(anyEvent(), ""));
	}
}
