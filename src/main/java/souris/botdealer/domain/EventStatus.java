/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

/**
 * Lifecycle of a betting event.
 *
 * <pre>
 *   DRAFT -> OPEN -> CLOSED -> SETTLED
 *              \        \
 *               \        -> CANCELLED (refunds every bet)
 *                -> CANCELLED
 * </pre>
 */
public enum EventStatus {

	DRAFT("event.status.draft"),
	OPEN("event.status.open"),
	CLOSED("event.status.closed"),
	SETTLED("event.status.settled"),
	CANCELLED("event.status.cancelled");

	private final String labelKey;

	EventStatus(String labelKey) {
		this.labelKey = labelKey;
	}

	public String labelKey() {
		return labelKey;
	}

	/** Bets are only accepted while the event is open. */
	public boolean acceptsBets() {
		return this == OPEN;
	}

	/** No further state change is possible once settled or cancelled. */
	public boolean isFinal() {
		return this == SETTLED || this == CANCELLED;
	}
}
