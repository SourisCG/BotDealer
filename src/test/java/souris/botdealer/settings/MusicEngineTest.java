/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The engine choice is persisted as text, so parsing must never throw: a bad or legacy
 * value has to degrade to AUTO instead of breaking startup.
 */
class MusicEngineTest {

	@Test
	void parsesStoredValuesIgnoringCaseAndPadding() {
		assertEquals(MusicEngine.YTDLP, MusicEngine.fromStored("ytdlp"));
		assertEquals(MusicEngine.YTDLP, MusicEngine.fromStored("  YTDLP  "));
		assertEquals(MusicEngine.YOUTUBE_DIRECT, MusicEngine.fromStored("youtube_direct"));
		assertEquals(MusicEngine.AUTO, MusicEngine.fromStored("auto"));
	}

	@Test
	void fallsBackToAutoForMissingOrUnknownValues() {
		assertEquals(MusicEngine.AUTO, MusicEngine.fromStored(null));
		assertEquals(MusicEngine.AUTO, MusicEngine.fromStored(""));
		assertEquals(MusicEngine.AUTO, MusicEngine.fromStored("   "));
		assertEquals(MusicEngine.AUTO, MusicEngine.fromStored("something-from-the-future"));
	}

	@Test
	void everyEngineHasLocalizationKeys() {
		for (MusicEngine engine : MusicEngine.values()) {
			assertNotNull(engine.titleKey());
			assertNotNull(engine.descriptionKey());
			assertEquals("wizard.music.engine.", engine.titleKey().substring(0, "wizard.music.engine.".length()));
		}
	}
}
