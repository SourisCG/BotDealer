/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.i18n;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The English (default) and Spanish bundles must always define exactly the same keys,
 * otherwise the UI shows raw "!key!" placeholders in one language.
 *
 * <p>The key-shape check is a regression guard: a multi-line value that forgets its
 * {@code \n} escapes silently becomes a bogus key such as {@code 2.} or {@code Keep}.</p>
 */
class I18nParityTest {

	/** Keys are dotted lower-case identifiers; anything else is a parsing accident. */
	private static final Pattern SANE_KEY = Pattern.compile("[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9]+)*");

	private static ResourceBundle english() {
		return ResourceBundle.getBundle("i18n/messages", Locale.ENGLISH);
	}

	private static ResourceBundle spanish() {
		return ResourceBundle.getBundle("i18n/messages", Locale.of("es"));
	}

	@Test
	void englishAndSpanishBundlesHaveSameKeys() {
		assertEquals(english().keySet(), spanish().keySet(),
			"i18n key mismatch between EN and ES bundles");
		assertTrue(english().keySet().contains("app.title"), "bundle must define app.title");
	}

	@Test
	void everyKeyLooksLikeAKey() {
		for (ResourceBundle bundle : new ResourceBundle[] {english(), spanish()}) {
			for (String key : bundle.keySet()) {
				assertTrue(SANE_KEY.matcher(key).matches(),
					"Suspicious i18n key '" + key + "' (a multi-line value is probably missing \\n escapes)");
			}
		}
	}

	@Test
	void noValueIsAccidentallyEmpty() {
		for (ResourceBundle bundle : new ResourceBundle[] {english(), spanish()}) {
			for (String key : bundle.keySet()) {
				assertFalse(bundle.getString(key).isBlank(), "Empty translation for " + key);
			}
		}
	}

	@Test
	void detectDefaultNeverReturnsNull() {
		assertNotNull(I18nService.detectDefault());
	}
}
