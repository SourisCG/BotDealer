/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

/**
 * How an event distributes the pot.
 *
 * <ul>
 *   <li>{@link #PARIMUTUEL} — the pot is split proportionally among the winners.</li>
 *   <li>{@link #FIXED_ODDS} — every bet is paid at the odds captured when it was placed.</li>
 * </ul>
 */
public enum PayoutMode {

	PARIMUTUEL("event.mode.parimutuel", "event.mode.parimutuel.hint"),
	FIXED_ODDS("event.mode.fixedOdds", "event.mode.fixedOdds.hint");

	private final String labelKey;
	private final String hintKey;

	PayoutMode(String labelKey, String hintKey) {
		this.labelKey = labelKey;
		this.hintKey = hintKey;
	}

	public String labelKey() {
		return labelKey;
	}

	public String hintKey() {
		return hintKey;
	}
}
