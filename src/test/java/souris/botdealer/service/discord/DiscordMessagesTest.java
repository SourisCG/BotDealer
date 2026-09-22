/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every translation key the Discord layer uses must exist in both bundles.
 *
 * <p>{@link DiscordMessages} exists precisely so this test is possible: it reflects over
 * the constants and fails the build on a typo, instead of letting a user discover
 * {@code !discord.reply.foo!} in a live channel.</p>
 */
class DiscordMessagesTest {

	private static final String ENGLISH_FILE = "/i18n/messages.properties";
	private static final String SPANISH_FILE = "/i18n/messages_es.properties";

	private static Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		try (var stream = DiscordMessagesTest.class.getResourceAsStream(resource)) {
			properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
		}
		return properties;
	}

	/** All public static String constants declared in {@link DiscordMessages}. */
	private static List<String> declaredKeys() throws IllegalAccessException {
		List<String> keys = new ArrayList<>();
		for (Field field : DiscordMessages.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
				keys.add((String) field.get(null));
			}
		}
		return keys;
	}

	@Test
	void everyDeclaredKeyExistsInBothBundles() throws Exception {
		Properties english = load(ENGLISH_FILE);
		Properties spanish = load(SPANISH_FILE);
		TreeSet<String> missing = new TreeSet<>();

		for (String key : declaredKeys()) {
			if (!english.containsKey(key)) {
				missing.add("en:" + key);
			}
			if (!spanish.containsKey(key)) {
				missing.add("es:" + key);
			}
		}

		assertTrue(missing.isEmpty(), "Discord messages missing from a bundle: " + missing);
	}

	@Test
	void noKeyIsDeclaredTwice() throws Exception {
		List<String> keys = declaredKeys();
		TreeSet<String> unique = new TreeSet<>(keys);

		assertTrue(unique.size() == keys.size(),
			"duplicate key constants: " + keys.stream().filter(k -> !unique.remove(k)).toList());
	}

	@Test
	void discordKeysAreNamespaced() throws Exception {
		for (String key : declaredKeys()) {
			assertTrue(key.startsWith("discord.") || key.startsWith("bot.") || key.startsWith("bet.")
					|| key.startsWith("event.") || key.startsWith("ledger."),
				"unexpected namespace for key " + key);
		}
	}
}
