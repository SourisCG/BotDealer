/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.i18n;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The English and Spanish bundles must always define exactly the same keys,
 * otherwise the UI shows raw "!key!" placeholders in one language.
 */
class I18nParityTest {

	@Test
	void englishAndSpanishBundlesHaveSameKeys() {
		ResourceBundle en = ResourceBundle.getBundle("i18n/messages", Locale.ENGLISH);
		ResourceBundle es = ResourceBundle.getBundle("i18n/messages", Locale.of("es"));

		assertEquals(en.keySet(), es.keySet(), "i18n key mismatch between EN and ES bundles");
		assertTrue(en.keySet().contains("app.title"), "bundle must define app.title");
	}

	@Test
	void detectDefaultNeverReturnsNull() {
		assertNotNull(I18nService.detectDefault());
	}
}
