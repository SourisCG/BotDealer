/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money rules for the play currency.
 *
 * <p>Two rounding modes exist on purpose:</p>
 * <ul>
 *   <li>{@link #normalize} rounds user input half-up (what the user typed).</li>
 *   <li>{@link #floor} truncates payouts down, so the sum of payouts can never exceed
 *       the pool. The leftover cents are re-distributed by
 *       {@code BettingMath.apportion}.</li>
 * </ul>
 */
public final class Money {

	public static final int SCALE = 2;
	public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

	private Money() {
	}

	/** Rounds user-provided amounts to the currency scale. */
	public static BigDecimal normalize(BigDecimal value) {
		return value == null ? ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
	}

	/** Truncates a computed payout so rounding never creates money. */
	public static BigDecimal floor(BigDecimal value) {
		return value == null ? ZERO : value.setScale(SCALE, RoundingMode.DOWN);
	}

	public static boolean isPositive(BigDecimal value) {
		return value != null && value.signum() > 0;
	}

	public static boolean isZeroOrPositive(BigDecimal value) {
		return value != null && value.signum() >= 0;
	}

	/** Formats a percentage (0-100) with up to two decimals for display. */
	public static String percent(BigDecimal value) {
		return value == null ? "0" : value.stripTrailingZeros().toPlainString();
	}
}
