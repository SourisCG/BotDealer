/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The yt-dlp side of the engine layer.
 *
 * <p>An interface so {@link EngineRouter} can be tested against the whole fallback
 * matrix without spawning yt-dlp or touching the network.</p>
 */
public interface StreamResolver {

	boolean isAvailable();

	/** Resolves a direct audio stream URL without downloading. */
	Optional<String> resolveStreamUrl(String normalizedTarget);

	/** Downloads the audio into the cache and returns the file. */
	Optional<Path> download(String normalizedTarget, String baseName);

	Optional<String> version();

	Optional<String> denoVersion();
}
