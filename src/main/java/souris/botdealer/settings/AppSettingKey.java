/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.settings;

/**
 * Every persisted application setting, with its default value.
 *
 * <p>Stable string keys (never rename one without a migration). Values are stored as
 * text and parsed on read; unparseable values fall back to the default instead of
 * breaking startup.</p>
 */
public enum AppSettingKey {

	/** UI language: {@code en} or {@code es}. */
	LANGUAGE("app.language", "en"),

	/** False until the first-run wizard is completed. */
	ONBOARDING_COMPLETE("app.onboardingComplete", "false"),

	CURRENCY_SINGULAR("economy.currency.singular", "chorizo"),
	CURRENCY_PLURAL("economy.currency.plural", "chorizos"),
	CURRENCY_SYMBOL("economy.currency.symbol", "\uD83C\uDF2D"),
	STARTING_BALANCE("economy.startingBalance", "100"),
	DAILY_AMOUNT("economy.dailyAmount", "25"),
	DAILY_COOLDOWN_HOURS("economy.dailyCooldownHours", "24"),
	RAKE_PERCENT("economy.rakePercent", "0"),

	/** Music engine: {@code AUTO}, {@code YOUTUBE_DIRECT} or {@code YTDLP}. */
	MUSIC_ENGINE("music.engine", "AUTO"),
	MUSIC_DEFAULT_VOLUME("music.defaultVolume", "100"),
	MUSIC_MAX_QUEUE_SIZE("music.maxQueueSize", "100"),
	MUSIC_MAX_TRACK_MINUTES("music.maxTrackMinutes", "30"),
	MUSIC_FOLDER("music.folder", ""),
	/** Media cache cap in megabytes; 0 disables the cache. */
	MUSIC_CACHE_MAX_MB("music.cacheMaxMb", "2048"),

	TTS_ENABLED("tts.enabled", "true"),
	/** Empty means "use the engine default voice". */
	TTS_VOICE("tts.voice", ""),
	TTS_SPEED("tts.speed", "1.0"),
	TTS_READ_ALOUD("tts.readAloud", "false");

	private final String key;
	private final String defaultValue;

	AppSettingKey(String key, String defaultValue) {
		this.key = key;
		this.defaultValue = defaultValue;
	}

	public String key() {
		return key;
	}

	public String defaultValue() {
		return defaultValue;
	}
}
