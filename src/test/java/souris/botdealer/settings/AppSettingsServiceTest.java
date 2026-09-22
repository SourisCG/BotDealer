/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.settings;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import souris.botdealer.repository.AppSettingRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AppSettingsServiceTest {

	@Autowired
	private AppSettingsService settings;

	@Autowired
	private AppSettingRepository repository;

	@BeforeEach
	void cleanSlate() {
		repository.deleteAllInBatch();
	}

	@Test
	void fallsBackToDefaultsWhenNothingIsStored() {
		assertEquals("chorizo", settings.get(AppSettingKey.CURRENCY_SINGULAR));
		assertEquals("chorizos", settings.get(AppSettingKey.CURRENCY_PLURAL));
		assertEquals(100, settings.getInt(AppSettingKey.STARTING_BALANCE));
		assertEquals(24, settings.getInt(AppSettingKey.DAILY_COOLDOWN_HOURS));
		assertEquals("AUTO", settings.get(AppSettingKey.MUSIC_ENGINE));
		assertEquals(0, settings.getDecimal(AppSettingKey.RAKE_PERCENT).compareTo(BigDecimal.ZERO));
		assertFalse(settings.isOnboardingComplete());
	}

	@Test
	void storesAndReadsBackTypedValues() {
		settings.set(AppSettingKey.CURRENCY_PLURAL, "patacones");
		settings.set(AppSettingKey.STARTING_BALANCE, "250");
		settings.set(AppSettingKey.RAKE_PERCENT, "2.5");
		settings.set(AppSettingKey.TTS_ENABLED, "true");

		assertEquals("patacones", settings.get(AppSettingKey.CURRENCY_PLURAL));
		assertEquals(250, settings.getInt(AppSettingKey.STARTING_BALANCE));
		assertEquals(0, settings.getDecimal(AppSettingKey.RAKE_PERCENT).compareTo(new BigDecimal("2.5")));
		assertTrue(settings.getBoolean(AppSettingKey.TTS_ENABLED));
	}

	@Test
	void overwritesRatherThanDuplicating() {
		settings.set(AppSettingKey.CURRENCY_SINGULAR, "chorizo");
		settings.set(AppSettingKey.CURRENCY_SINGULAR, "tocino");

		assertEquals("tocino", settings.get(AppSettingKey.CURRENCY_SINGULAR));
		assertEquals(1, repository.count());
	}

	@Test
	void survivesCorruptedNumbersWithoutThrowing() {
		settings.set(AppSettingKey.STARTING_BALANCE, "not-a-number");

		assertEquals(100, settings.getInt(AppSettingKey.STARTING_BALANCE));
	}

	@Test
	void treatsBlankValuesAsMissing() {
		settings.set(AppSettingKey.MUSIC_ENGINE, "   ");

		assertEquals("AUTO", settings.get(AppSettingKey.MUSIC_ENGINE));
	}

	@Test
	void resetsASingleKeyToItsDefault() {
		settings.set(AppSettingKey.CURRENCY_PLURAL, "patacones");

		settings.reset(AppSettingKey.CURRENCY_PLURAL);

		assertEquals("chorizos", settings.get(AppSettingKey.CURRENCY_PLURAL));
	}

	@Test
	void roundTripsLanguageAndOnboarding() {
		settings.setLanguage(Locale.of("es"));
		assertEquals("es", settings.getLanguage().getLanguage());

		settings.setLanguage(Locale.ENGLISH);
		assertEquals("en", settings.getLanguage().getLanguage());

		settings.setOnboardingComplete(true);
		assertTrue(settings.isOnboardingComplete());
	}

	@Test
	void snapshotContainsEveryKey() {
		Map<AppSettingKey, String> snapshot = settings.snapshot();

		assertEquals(AppSettingKey.values().length, snapshot.size());
		for (AppSettingKey key : AppSettingKey.values()) {
			assertEquals(settings.get(key), snapshot.get(key), "snapshot must mirror the resolved value of " + key);
		}
	}

	@Test
	void replaceAllAppliesEveryValue() {
		settings.replaceAll(Map.of(
			AppSettingKey.CURRENCY_SINGULAR, "ficha",
			AppSettingKey.CURRENCY_PLURAL, "fichas",
			AppSettingKey.STARTING_BALANCE, "500"));

		assertEquals("ficha", settings.get(AppSettingKey.CURRENCY_SINGULAR));
		assertEquals("fichas", settings.get(AppSettingKey.CURRENCY_PLURAL));
		assertEquals(500, settings.getInt(AppSettingKey.STARTING_BALANCE));
	}
}
