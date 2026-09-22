/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

/**
 * Events the UI reacts to. Kept as small records so listeners can pattern-match on the
 * type they care about instead of polling services.
 */
public final class UiEvents {

	private UiEvents() {
	}

	/** The bot connection changed state; {@code detailKey} is an i18n key or empty. */
	public record BotStatusChanged(BotStatus status, String detailKey) {
	}

	/** An event was created, opened, closed, settled or cancelled. */
	public record EventChanged(long eventId) {
	}

	/** A balance changed (bet placed, payout, daily reward, admin action). */
	public record EconomyChanged(long guildId) {
	}

	/** A guild configuration was edited from the UI. */
	public record GuildConfigChanged(long guildId) {
	}
}
