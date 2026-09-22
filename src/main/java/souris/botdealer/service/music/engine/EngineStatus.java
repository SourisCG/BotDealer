/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.nio.file.Path;

import souris.botdealer.settings.MusicEngine;

/**
 * What the music layer can currently do, as shown in the Music screen.
 *
 * @param mode            the engine chosen in Settings
 * @param directAvailable whether the in-process engine is usable
 * @param ytDlpAvailable  whether the yt-dlp binary was found and runs
 * @param denoAvailable   whether the JavaScript runtime is present (needed for full
 *                        YouTube support since late 2025)
 * @param ytDlpVersion    reported version, empty when unknown
 * @param denoVersion     reported version, empty when unknown
 * @param enginesDir      where the engine binaries live, so the UI can open it
 */
public record EngineStatus(MusicEngine mode, boolean directAvailable, boolean ytDlpAvailable,
		boolean denoAvailable, String ytDlpVersion, String denoVersion, Path enginesDir) {

	/** True when at least one engine can resolve a request. */
	public boolean anyAvailable() {
		return directAvailable || ytDlpAvailable;
	}

	/** Short, human-readable summary for the status card. */
	public String summaryKey() {
		if (!anyAvailable()) {
			return "music.engine.status.none";
		}
		if (directAvailable && ytDlpAvailable) {
			return "music.engine.status.both";
		}
		return directAvailable ? "music.engine.status.directOnly" : "music.engine.status.ytDlpOnly";
	}
}
