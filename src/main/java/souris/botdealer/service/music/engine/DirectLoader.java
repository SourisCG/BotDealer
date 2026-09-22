/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

/**
 * The in-process engine (LavaPlayer + youtube-source).
 *
 * <p>Declared here and implemented by the playback layer so the router can orchestrate
 * the fallback without depending on LavaPlayer's concrete setup.</p>
 *
 * <p>The identifier may be a YouTube URL, a search prefix, an {@code http} stream URL or
 * a local file path; LavaPlayer decides from the source managers registered for it.</p>
 */
public interface DirectLoader {

	/** Attempts a load, calling the handler exactly once. */
	void load(String identifier, AudioLoadResultHandler handler);

	/** True when the source managers are registered and ready. */
	boolean isAvailable();

	/** Source names for diagnostics, e.g. {@code youtube}, {@code local}. */
	List<String> sourceNames();

	/** Loads a local file produced by the yt-dlp download path. */
	default void loadFile(Path file, AudioLoadResultHandler handler) {
		load(file.toAbsolutePath().toString(), handler);
	}

	/** Convenience for callers that only need to know whether something is playable. */
	default Optional<String> describe() {
		return isAvailable() ? Optional.of(String.join(", ", sourceNames())) : Optional.empty();
	}
}
