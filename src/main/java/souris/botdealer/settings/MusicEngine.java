/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.settings;

/**
 * Music resolution strategy, persisted as {@code music.engine}.
 *
 * <ul>
 *   <li>{@link #AUTO} — try the in-process engine, fall back to yt-dlp on failure</li>
 *   <li>{@link #YOUTUBE_DIRECT} — in-process only (youtube-source), no extra components</li>
 *   <li>{@link #YTDLP} — always use the bundled yt-dlp/Deno engine</li>
 * </ul>
 */
public enum MusicEngine {

	AUTO("wizard.music.engine.auto.title", "wizard.music.engine.auto.body"),
	YOUTUBE_DIRECT("wizard.music.engine.direct.title", "wizard.music.engine.direct.body"),
	YTDLP("wizard.music.engine.ytdlp.title", "wizard.music.engine.ytdlp.body");

	private final String titleKey;
	private final String descriptionKey;

	MusicEngine(String titleKey, String descriptionKey) {
		this.titleKey = titleKey;
		this.descriptionKey = descriptionKey;
	}

	public String titleKey() {
		return titleKey;
	}

	public String descriptionKey() {
		return descriptionKey;
	}

	/** Parses a stored value, falling back to {@link #AUTO} for anything unknown. */
	public static MusicEngine fromStored(String value) {
		if (value == null || value.isBlank()) {
			return AUTO;
		}
		try {
			return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return AUTO;
		}
	}
}
