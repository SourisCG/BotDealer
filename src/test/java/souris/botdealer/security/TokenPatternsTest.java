/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import souris.botdealer.TestTokens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenPatternsTest {

	private static final String TOKEN = TestTokens.FAKE;

	@Test
	void recognisesAValidTokenShape() {
		assertTrue(TokenPatterns.looksLikeDiscordToken(TOKEN));
		assertTrue(TokenPatterns.looksLikeDiscordToken("  " + TOKEN + "  "));
	}

	@Test
	void rejectsNonTokens() {
		assertFalse(TokenPatterns.looksLikeDiscordToken(null));
		assertFalse(TokenPatterns.looksLikeDiscordToken(""));
		assertFalse(TokenPatterns.looksLikeDiscordToken("hello world"));
		assertFalse(TokenPatterns.looksLikeDiscordToken("abc.def.ghi"));
	}

	@Test
	void redactsTokensInsideSentences() {
		String message = "GET /users/@me failed for " + TOKEN + " (403)";
		String redacted = TokenPatterns.redact(message);

		assertFalse(redacted.contains("GxXxXx"));
		assertTrue(redacted.contains(TokenPatterns.REDACTED));
		assertTrue(redacted.contains("GET /users/@me failed for"));
	}

	@Test
	void redactionIsStableForCleanMessages() {
		assertEquals("nothing to hide", TokenPatterns.redact("nothing to hide"));
	}

	@Test
	void maskKeepsOnlyTheLastFourCharacters() {
		assertEquals("\u2022\u2022\u2022\u2022" + TOKEN.substring(TOKEN.length() - 4),
			TokenPatterns.maskForDisplay(TOKEN));
	}

	@Test
	void maskHandlesShortAndEmptyInput() {
		assertEquals("", TokenPatterns.maskForDisplay(null));
		assertEquals("", TokenPatterns.maskForDisplay("   "));
		assertEquals("\u2022\u2022\u2022\u2022", TokenPatterns.maskForDisplay("ab"));
	}
}
