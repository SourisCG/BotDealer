/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

/** Speech state the UI reacts to. */
public final class TtsEvents {

	private TtsEvents() {
	}

	/** A phrase was synthesized and queued; {@code durationMillis} drives the duck restore. */
	public record SpeechQueued(long guildId, String text, long durationMillis) {
	}

	/** Speech could not be produced; {@code messageKey} is an i18n key. */
	public record SpeechFailed(long guildId, String messageKey, String detail) {
	}

	/** The installed voice list changed (download, delete, rescan). */
	public record VoicesChanged() {
	}
}
