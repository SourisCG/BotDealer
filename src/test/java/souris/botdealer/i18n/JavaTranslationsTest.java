/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.i18n;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Catches dangling translation keys in Java sources.
 *
 * <p>Services report problems as i18n keys ({@code new BetValidationException("bet.error.x")}),
 * and {@code I18nService} renders an unknown key as {@code !key!}. Without this test a
 * typo would only surface in front of a real user.</p>
 *
 * <p>Only strings starting with a known namespace are considered, so ordinary dotted
 * strings cannot produce false positives.</p>
 */
class JavaTranslationsTest {

	private static final List<String> NAMESPACES = List.of(
		"app", "nav", "shell", "wizard", "settings", "guilds", "wallets", "events", "dashboard",
		"sections", "about", "discord", "bet", "event", "ledger", "music", "bot");

	private static final Pattern KEY_LITERAL = Pattern.compile(
		"\"((?:" + String.join("|", NAMESPACES) + ")\\.[a-zA-Z][a-zA-Z0-9_.]*)\"");

	/** Literals that look like keys but are hostnames, e.g. {@code music.youtube.com}. */
	private static final Pattern HOSTNAME = Pattern.compile(".*\\.(com|org|net|io|dev)$");

	@Test
	void everyKeyUsedInJavaExistsInBothBundles() throws IOException {
		Properties english = load("/i18n/messages.properties");
		Properties spanish = load("/i18n/messages_es.properties");
		TreeSet<String> missing = new TreeSet<>();

		java.util.Set<String> ignored = settingKeys();
		for (String key : keysUsedInJava()) {
			if (ignored.contains(key) || HOSTNAME.matcher(key).matches()) {
				continue;
			}
			if (!english.containsKey(key)) {
				missing.add("en:" + key);
			}
			if (!spanish.containsKey(key)) {
				missing.add("es:" + key);
			}
		}

		assertTrue(missing.isEmpty(), "Java references keys missing from a bundle: " + missing);
	}

	@Test
	void theScannerFindsKeys() throws IOException {
		assertFalse(keysUsedInJava().isEmpty(), "no keys found; the scanner is broken");
	}

	/**
	 * Settings are stored under dotted keys too ({@code music.engine} is a setting, not a
	 * translation), so the values declared in {@code AppSettingKey} are read from source
	 * and excluded.
	 */
	private static java.util.Set<String> settingKeys() throws IOException {
		java.util.Set<String> keys = new java.util.HashSet<>();
		Matcher matcher = Pattern.compile("\"([a-z][a-zA-Z0-9]*(?:\\.[a-zA-Z0-9]+)+)\"")
			.matcher(Files.readString(Path.of("src/main/java/souris/botdealer/settings/AppSettingKey.java")));
		while (matcher.find()) {
			keys.add(matcher.group(1));
		}
		return keys;
	}

	private static List<String> keysUsedInJava() throws IOException {
		Path root = Path.of("src/main/java");
		TreeSet<String> keys = new TreeSet<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				Matcher matcher = KEY_LITERAL.matcher(Files.readString(file));
				while (matcher.find()) {
					keys.add(matcher.group(1));
				}
			}
		}
		return List.copyOf(keys);
	}

	private static Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		try (var stream = JavaTranslationsTest.class.getResourceAsStream(resource)) {
			properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
		}
		return properties;
	}
}
