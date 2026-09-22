/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music;

/** Queue repeat behaviour, as shown in the Music screen and used by the `/loop` command. */
public enum RepeatMode {

	OFF("music.repeat.off"),
	TRACK("music.repeat.track"),
	QUEUE("music.repeat.queue");

	private final String labelKey;

	RepeatMode(String labelKey) {
		this.labelKey = labelKey;
	}

	public String labelKey() {
		return labelKey;
	}

	/** Cycles OFF -> TRACK -> QUEUE -> OFF, for a single toggle command. */
	public RepeatMode next() {
		return switch (this) {
			case OFF -> TRACK;
			case TRACK -> QUEUE;
			case QUEUE -> OFF;
		};
	}

	public static RepeatMode fromStored(String value) {
		if (value == null || value.isBlank()) {
			return OFF;
		}
		try {
			return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return OFF;
		}
	}
}
