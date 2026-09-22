/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.i18n;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code %key} used in an FXML file must exist in both bundles.
 *
 * <p>FXML resolves those keys while loading, so a missing one only shows up when the
 * screen is opened — a class of failure that is easy to introduce and invisible to the
 * compiler. This test walks the FXML sources instead.</p>
 */
class FxmlTranslationsTest {

	private static final Pattern BUNDLE_KEY = Pattern.compile("\"%([a-zA-Z0-9_.]+)\"");

	@Test
	void everyKeyUsedInFxmlExistsInBothBundles() throws IOException {
		Properties english = load("/i18n/messages.properties");
		Properties spanish = load("/i18n/messages_es.properties");
		TreeSet<String> missing = new TreeSet<>();

		for (String key : keysUsedInFxml()) {
			if (!english.containsKey(key)) {
				missing.add("en:" + key);
			}
			if (!spanish.containsKey(key)) {
				missing.add("es:" + key);
			}
		}

		assertTrue(missing.isEmpty(), "FXML references keys missing from a bundle: " + missing);
	}

	@Test
	void theScanFindsKeysAtAll() throws IOException {
		// Guards the scanner itself: if the regex or the file walk breaks, the test above
		// would silently pass on an empty set.
		assertFalse(keysUsedInFxml().isEmpty(), "no %keys found in FXML; the scanner is broken");
	}

	private static List<String> keysUsedInFxml() throws IOException {
		Path fxmlRoot = Path.of("src/main/resources/fxml");
		List<String> keys = new ArrayList<>();
		try (Stream<Path> files = Files.walk(fxmlRoot)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".fxml")).toList()) {
				Matcher matcher = BUNDLE_KEY.matcher(Files.readString(file));
				while (matcher.find()) {
					keys.add(matcher.group(1));
				}
			}
		}
		return keys;
	}

	private static Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		URL url = FxmlTranslationsTest.class.getResource(resource);
		try (var stream = url.openStream()) {
			properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
		}
		return properties;
	}
}
