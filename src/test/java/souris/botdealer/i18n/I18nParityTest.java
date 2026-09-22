/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.i18n;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Translation integrity.
 *
 * <p>The bundles are read <b>as files</b>, not through {@code ResourceBundle}: a bundle
 * falls back to the default bundle for missing keys, so {@code keySet()} on a Spanish
 * bundle happily reports the English keys and a missing translation would go unnoticed
 * until a Spanish user saw English text.</p>
 */
class I18nParityTest {

	private static final String ENGLISH_FILE = "/i18n/messages.properties";
	private static final String SPANISH_FILE = "/i18n/messages_es.properties";

	/** Keys are dotted lower-case identifiers; anything else is a parsing accident. */
	private static final Pattern SANE_KEY = Pattern.compile("[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9]+)*");

	private static Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		try (var stream = I18nParityTest.class.getResourceAsStream(resource)) {
			assertNotNull(stream, "missing bundle: " + resource);
			properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
		}
		return properties;
	}

	@Test
	void englishAndSpanishDefineExactlyTheSameKeys() throws IOException {
		Set<String> english = load(ENGLISH_FILE).stringPropertyNames();
		Set<String> spanish = load(SPANISH_FILE).stringPropertyNames();

		Set<String> missingInSpanish = new TreeSet<>(english);
		missingInSpanish.removeAll(spanish);
		Set<String> missingInEnglish = new TreeSet<>(spanish);
		missingInEnglish.removeAll(english);

		assertTrue(missingInSpanish.isEmpty(), "missing in messages_es.properties: " + missingInSpanish);
		assertTrue(missingInEnglish.isEmpty(), "missing in messages.properties: " + missingInEnglish);
		assertEquals(english, spanish);
	}

	@Test
	void everyKeyLooksLikeAKey() throws IOException {
		for (String resource : new String[] {ENGLISH_FILE, SPANISH_FILE}) {
			for (String key : load(resource).stringPropertyNames()) {
				assertTrue(SANE_KEY.matcher(key).matches(),
					"Suspicious key '" + key + "' in " + resource
						+ " (a multi-line value is probably missing \\n escapes)");
			}
		}
	}

	@Test
	void noValueIsAccidentallyEmpty() throws IOException {
		for (String resource : new String[] {ENGLISH_FILE, SPANISH_FILE}) {
			Properties properties = load(resource);
			for (String key : properties.stringPropertyNames()) {
				assertFalse(properties.getProperty(key).isBlank(), "Empty translation for " + key);
			}
		}
	}

	@Test
	void bothBundlesStayRoughlyTheSameSize() throws IOException {
		// A translation that is suspiciously short is usually a placeholder left behind.
		Properties english = load(ENGLISH_FILE);
		Properties spanish = load(SPANISH_FILE);
		for (String key : english.stringPropertyNames()) {
			String en = english.getProperty(key);
			String es = spanish.getProperty(key);
			if (en.length() > 40) {
				assertTrue(es.length() > en.length() / 3,
					"Suspiciously short translation for " + key + ": '" + es + "'");
			}
		}
	}

	@Test
	void detectDefaultNeverReturnsNull() {
		assertNotNull(I18nService.detectDefault());
		assertTrue(Set.of("en", "es").contains(I18nService.detectDefault().getLanguage()));
		assertEquals("es", I18nService.SPANISH.getLanguage());
		assertEquals(Locale.ENGLISH, I18nService.ENGLISH);
	}
}
