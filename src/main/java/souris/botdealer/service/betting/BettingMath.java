/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.betting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import souris.botdealer.util.Money;

/**
 * Pure payout arithmetic. No Spring, no database: every rule that decides who gets how
 * much lives here so it can be tested exhaustively.
 *
 * <p>Parimutuel payouts use <b>largest-remainder apportionment</b>: exact shares are
 * truncated to cents and the leftover cents are handed to the winners with the largest
 * fractional part. That guarantees the payouts sum to exactly the net pool — no money is
 * created by rounding and none silently disappears.</p>
 *
 * <p>If nobody backed the winning option there is no one to pay; callers are expected to
 * refund every stake instead (see {@code EventService.settle}).</p>
 */
public final class BettingMath {

	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final BigDecimal ONE_CENT = new BigDecimal("0.01");
	private static final int EXACT_SCALE = 10;

	private BettingMath() {
	}

	/** One stake, identified by its bet so payouts can be mapped back. */
	public record Stake(long betId, long optionId, BigDecimal amount) {
	}

	/** The amount a single bet receives. */
	public record Payout(long betId, BigDecimal amount) {
	}

	// ------------------------------------------------------------------ pools

	public static BigDecimal totalPool(List<Stake> stakes) {
		return stakes.stream().map(Stake::amount).reduce(Money.ZERO, (a, b) -> a.add(b))
			.setScale(Money.SCALE, RoundingMode.HALF_UP);
	}

	public static BigDecimal poolForOption(List<Stake> stakes, long optionId) {
		return stakes.stream()
			.filter(stake -> stake.optionId() == optionId)
			.map(Stake::amount)
			.reduce(Money.ZERO, (a, b) -> a.add(b))
			.setScale(Money.SCALE, RoundingMode.HALF_UP);
	}

	/** House cut, truncated so it can never eat into the winners' share by rounding. */
	public static BigDecimal rakeAmount(BigDecimal totalPool, BigDecimal rakePercent) {
		if (rakePercent == null || rakePercent.signum() <= 0) {
			return Money.ZERO;
		}
		return Money.floor(totalPool.multiply(rakePercent).divide(HUNDRED, EXACT_SCALE, RoundingMode.HALF_UP));
	}

	public static BigDecimal netPool(BigDecimal totalPool, BigDecimal rakePercent) {
		return totalPool.subtract(rakeAmount(totalPool, rakePercent)).setScale(Money.SCALE, RoundingMode.HALF_UP);
	}

	// ------------------------------------------------------------------ payouts

	/**
	 * Splits the net pool among the bets that backed {@code winningOptionId}.
	 *
	 * @return one payout per winning bet, summing exactly to the net pool; empty when no
	 *         bet backed the winner (the caller must then refund instead)
	 */
	public static List<Payout> parimutuelPayouts(List<Stake> stakes, long winningOptionId,
			BigDecimal rakePercent) {
		List<Stake> winners = stakes.stream().filter(stake -> stake.optionId() == winningOptionId).toList();
		if (winners.isEmpty()) {
			return List.of();
		}
		BigDecimal total = totalPool(stakes);
		BigDecimal net = netPool(total, rakePercent);
		BigDecimal winnerStakes = poolForOption(stakes, winningOptionId);
		if (winnerStakes.signum() <= 0) {
			return List.of();
		}

		record Share(Stake stake, BigDecimal exact, BigDecimal truncated) {
		}

		List<Share> shares = new ArrayList<>(winners.size());
		BigDecimal truncatedTotal = Money.ZERO;
		for (Stake winner : winners) {
			BigDecimal exact = net.multiply(winner.amount())
				.divide(winnerStakes, EXACT_SCALE, RoundingMode.HALF_UP);
			BigDecimal truncated = Money.floor(exact);
			shares.add(new Share(winner, exact, truncated));
			truncatedTotal = truncatedTotal.add(truncated);
		}

		BigDecimal leftover = net.subtract(truncatedTotal);
		int leftoverCents = Math.min(
			leftover.movePointRight(Money.SCALE).setScale(0, RoundingMode.HALF_UP).intValue(),
			shares.size());

		// Largest fractional remainder first; ties broken by bet id so runs are reproducible.
		shares.sort(Comparator
			.comparing((Share share) -> share.exact().subtract(share.truncated()), Comparator.reverseOrder())
			.thenComparing(share -> share.stake().betId()));

		List<Payout> payouts = new ArrayList<>(shares.size());
		for (int index = 0; index < shares.size(); index++) {
			BigDecimal amount = shares.get(index).truncated();
			if (index < leftoverCents) {
				amount = amount.add(ONE_CENT);
			}
			payouts.add(new Payout(shares.get(index).stake().betId(), amount));
		}
		return payouts;
	}

	/** Payout of a single fixed-odds bet, truncated down. */
	public static BigDecimal fixedOddsPayout(BigDecimal stake, BigDecimal odds) {
		if (stake == null || odds == null || stake.signum() <= 0 || odds.signum() <= 0) {
			return Money.ZERO;
		}
		return Money.floor(stake.multiply(odds));
	}

	/**
	 * Fixed-odds payouts for the winning bets, using the odds frozen at placement time.
	 * Unlike parimutuel there is no apportionment: the bookmaker carries the risk.
	 */
	public static List<Payout> fixedOddsPayouts(List<Stake> stakes, long winningOptionId,
			Map<Long, BigDecimal> oddsByBetId) {
		List<Payout> payouts = new ArrayList<>();
		for (Stake stake : stakes) {
			if (stake.optionId() != winningOptionId) {
				continue;
			}
			BigDecimal odds = oddsByBetId.get(stake.betId());
			payouts.add(new Payout(stake.betId(), fixedOddsPayout(stake.amount(), odds)));
		}
		return payouts;
	}

	/** Total the creator would owe if the given option wins; shown as a risk warning. */
	public static BigDecimal fixedOddsLiability(List<Stake> stakes, long optionId,
			Map<Long, BigDecimal> oddsByBetId) {
		return fixedOddsPayouts(stakes, optionId, oddsByBetId).stream()
			.map(Payout::amount)
			.reduce(Money.ZERO, BigDecimal::add);
	}

	/** Decimal odds implied by a parimutuel pool, for display only. */
	public static BigDecimal impliedOdds(BigDecimal optionPool, BigDecimal totalPool, BigDecimal rakePercent) {
		if (optionPool == null || optionPool.signum() <= 0) {
			return Money.ZERO;
		}
		return netPool(totalPool, rakePercent).divide(optionPool, 2, RoundingMode.DOWN);
	}
}
