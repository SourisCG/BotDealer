/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.settings;

import souris.botdealer.TestTokens;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import souris.botdealer.repository.AppSettingRepository;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reset wipes rows, not the schema, so the assertions can use the same context.
 * {@code @DirtiesContext} keeps the wipe from leaking into other test classes.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResetServiceTest {

	private static final String TOKEN = TestTokens.FAKE;

	@Autowired
	private ResetService resetService;

	@Autowired
	private AppSettingsService settings;

	@Autowired
	private SecretService secrets;

	@Autowired
	private AppSettingRepository repository;

	@Test
	void wipesSettingsAndCredentials() {
		settings.set(AppSettingKey.CURRENCY_PLURAL, "patacones");
		settings.setOnboardingComplete(true);
		secrets.set(SecretKey.DISCORD_TOKEN, TOKEN);

		resetService.resetEverything();

		assertEquals(0, repository.count());
		assertFalse(secrets.isSet(SecretKey.DISCORD_TOKEN));
		assertFalse(settings.isOnboardingComplete());
		assertEquals("chorizos", settings.get(AppSettingKey.CURRENCY_PLURAL));
	}

	@Test
	void removesDownloadedAssetsButKeepsTheDatabaseFolder() throws Exception {
		Path voices = souris.botdealer.config.AppPaths.dataDir().resolve("voices");
		Path nested = voices.resolve("es_ES-sharvard-medium");
		Files.createDirectories(nested);
		Files.writeString(nested.resolve("voice.onnx"), "fake-model");

		resetService.resetEverything();

		assertFalse(Files.exists(nested), "downloaded voice files must be removed by a reset");
		assertTrue(Files.isDirectory(souris.botdealer.config.AppPaths.dbDir()),
			"the database folder itself must survive a reset");
	}

	@Test
	void forgettingCredentialsKeepsTheEconomyData() {
		settings.set(AppSettingKey.CURRENCY_PLURAL, "patacones");
		secrets.set(SecretKey.DISCORD_TOKEN, TOKEN);

		resetService.forgetCredentials();

		assertFalse(secrets.isSet(SecretKey.DISCORD_TOKEN));
		assertEquals("patacones", settings.get(AppSettingKey.CURRENCY_PLURAL));
	}
}
