/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.logging;

import souris.botdealer.TestTokens;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Discord token must never survive the logging pipeline, even by accident.
 */
class RedactingConvertersTest {

	private static final String TOKEN = TestTokens.FAKE;

	private final RedactingMessageConverter messageConverter = new RedactingMessageConverter();
	private final RedactingThrowableConverter throwableConverter = new RedactingThrowableConverter();

	private static ILoggingEvent eventWith(String message) {
		ILoggingEvent event = mock(ILoggingEvent.class);
		when(event.getFormattedMessage()).thenReturn(message);
		return event;
	}

	@Test
	void masksClassicBotTokenInMessages() {
		String out = messageConverter.convert(eventWith("login with " + TOKEN + " done"));

		assertEquals("login with ***REDACTED-SECRET*** done", out);
		assertFalse(out.contains("GxXxXx"), "token fragment leaked into logs");
	}

	@Test
	void masksMfaTokens() {
		assertEquals("key=***REDACTED-SECRET***",
			messageConverter.convert(eventWith("key=" + TestTokens.FAKE_MFA)));
	}

	@Test
	void leavesNormalMessagesUntouched() {
		assertEquals("BotDealer UI started (locale=en)",
			messageConverter.convert(eventWith("BotDealer UI started (locale=en)")));
	}

	@Test
	void rendersNothingWhenThereIsNoThrowable() {
		assertEquals("", throwableConverter.convert(eventWith("no exception")));
	}

	@Test
	void masksTokensInsideStackTraces() {
		ILoggingEvent event = mock(ILoggingEvent.class);
		// A real proxy so the converter runs the real formatting path.
		IThrowableProxy proxy = new ThrowableProxy(new IllegalStateException("request failed for " + TOKEN));
		when(event.getThrowableProxy()).thenReturn(proxy);

		String rendered = throwableConverter.convert(event);

		assertTrue(rendered.contains("java.lang.IllegalStateException"));
		assertTrue(rendered.contains("request failed for"));
		assertFalse(rendered.contains("GxXxXx"), "token leaked through the stack trace");
	}
}
