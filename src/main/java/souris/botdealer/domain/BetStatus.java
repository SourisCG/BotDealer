/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

/** Outcome of a single bet once its event has been settled or cancelled. */
public enum BetStatus {

	/** Money is committed; the event has not been resolved yet. */
	PLACED("bet.status.placed"),

	/** The chosen option won and the payout was credited. */
	WON("bet.status.won"),

	/** The chosen option lost; the stake is gone. */
	LOST("bet.status.lost"),

	/** The event was cancelled and the stake was returned. */
	REFUNDED("bet.status.refunded");

	private final String labelKey;

	BetStatus(String labelKey) {
		this.labelKey = labelKey;
	}

	public String labelKey() {
		return labelKey;
	}

	public boolean isResolved() {
		return this != PLACED;
	}
}
