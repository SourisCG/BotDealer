/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import souris.botdealer.service.music.engine.EngineInstaller;
import souris.botdealer.service.tts.VoiceRegistry;

/**
 * Copies the bundled engines and voices into the writable data folder on first launch.
 *
 * <p>An installed app image is read-only ({@code /opt}, Program Files) and the engines
 * must be able to update themselves, so the shipped copies are seeds, not the working
 * copies. Seeding skips files that already exist, which means a later launch only walks
 * the directory.</p>
 *
 * <p>Runs on {@code ApplicationReadyEvent} so a slow first-run copy does not delay the
 * window appearing.</p>
 */
@Component
public class AssetSeeder {

	private static final Logger log = LoggerFactory.getLogger(AssetSeeder.class);

	private final EngineInstaller engines;
	private final VoiceRegistry voices;

	public AssetSeeder(EngineInstaller engines, VoiceRegistry voices) {
		this.engines = engines;
		this.voices = voices;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void seed() {
		Thread.ofVirtual().name("asset-seeder").start(() -> {
			try {
				var seededEngines = engines.seedFromBundle();
				if (!seededEngines.isEmpty()) {
					log.info("Prepared {} bundled engine file(s)", seededEngines.size());
				}
				// VoiceRegistry seeds as part of its first scan.
				int voiceCount = voices.voices().size();
				log.info("{} voice(s) available", voiceCount);
			} catch (Exception e) {
				log.warn("Could not prepare the bundled assets: {}", e.toString());
			}
		});
	}
}
