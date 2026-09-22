/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.betting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive checks of the payout rules. The invariant that matters most: the payouts of
 * a parimutuel event must sum to exactly the net pool, never more and never less.
 */
class BettingMathTest {

	private static final BigDecimal NO_RAKE = BigDecimal.ZERO;

	private static BettingMath.Stake stake(long betId, long optionId, String amount) {
		return new BettingMath.Stake(betId, optionId, new BigDecimal(amount));
	}

	private static BigDecimal sum(List<BettingMath.Payout> payouts) {
		return payouts.stream().map(BettingMath.Payout::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static BigDecimal payoutOf(List<BettingMath.Payout> payouts, long betId) {
		return payouts.stream().filter(payout -> payout.betId() == betId).map(BettingMath.Payout::amount)
			.findFirst().orElse(BigDecimal.ZERO);
	}

	// ------------------------------------------------------------------ pools

	@Test
	void poolsAddUpPerOptionAndInTotal() {
		List<BettingMath.Stake> stakes = List.of(
			stake(1, 10, "10.00"),
			stake(2, 10, "5.50"),
			stake(3, 20, "4.50"));

		assertEquals(new BigDecimal("20.00"), BettingMath.totalPool(stakes));
		assertEquals(new BigDecimal("15.50"), BettingMath.poolForOption(stakes, 10));
		assertEquals(new BigDecimal("4.50"), BettingMath.poolForOption(stakes, 20));
		assertEquals(new BigDecimal("0.00"), BettingMath.poolForOption(stakes, 99));
	}

	@Test
	void rakeIsTruncatedSoItCannotEatIntoThePool() {
		// 3% of 10.01 is 0.3003 -> 0.30
		assertEquals(new BigDecimal("0.30"), BettingMath.rakeAmount(new BigDecimal("10.01"),
			new BigDecimal("3")));
		assertEquals(new BigDecimal("9.71"), BettingMath.netPool(new BigDecimal("10.01"),
			new BigDecimal("3")));
		assertEquals(new BigDecimal("10.00"), BettingMath.netPool(new BigDecimal("10.00"), NO_RAKE));
	}

	// ------------------------------------------------------------------ parimutuel

	@Test
	void singleWinnerTakesTheWholePot() {
		List<BettingMath.Stake> stakes = List.of(
			stake(1, 10, "10.00"),
			stake(2, 20, "5.00"));

		List<BettingMath.Payout> payouts = BettingMath.parimutuelPayouts(stakes, 10, NO_RAKE);

		assertEquals(1, payouts.size());
		assertEquals(new BigDecimal("15.00"), payoutOf(payouts, 1));
	}

	@Test
	void winnersShareProportionally() {
		List<BettingMath.Stake> stakes = List.of(
			stake(1, 10, "30.00"),
			stake(2, 10, "10.00"),
			stake(3, 20, "60.00"));

		List<BettingMath.Payout> payouts = BettingMath.parimutuelPayouts(stakes, 10, NO_RAKE);

		// winner pool 40, total 100 -> 30/40 and 10/40 of 100
		assertEquals(new BigDecimal("75.00"), payoutOf(payouts, 1));
		assertEquals(new BigDecimal("25.00"), payoutOf(payouts, 2));
	}

	@Test
	void awkwardSplitStillSumsToTheExactNetPool() {
		// 1 staked on the winner, two others on a losing option: 3 / 1 = 3.00 each.
		// Use thirds to force a repeating decimal.
		List<BettingMath.Stake> stakes = List.of(
			stake(1, 10, "10.00"),
			stake(2, 10, "10.00"),
			stake(3, 10, "10.00"),
			stake(4, 20, "10.00"));

		List<BettingMath.Payout> payouts = BettingMath.parimutuelPayouts(stakes, 10, NO_RAKE);

		// total 40, winner pool 30 -> 13.333... each, truncated to 13.33 = 39.99, one cent left
		assertEquals(new BigDecimal("40.00"), sum(payouts));
		assertEquals(new BigDecimal("13.34"), payoutOf(payouts, 1));
		assertEquals(new BigDecimal("13.33"), payoutOf(payouts, 2));
		assertEquals(new BigDecimal("13.33"), payoutOf(payouts, 3));
	}

	@Test
	void manyWinnersNeverLoseMoneyToRounding() {
		List<BettingMath.Stake> stakes = new java.util.ArrayList<>();
		for (int i = 1; i <= 7; i++) {
			stakes.add(stake(i, 10, "1.00"));
		}
		stakes.add(stake(100, 20, "3.00"));

		List<BettingMath.Payout> payouts = BettingMath.parimutuelPayouts(stakes, 10, NO_RAKE);

		assertEquals(BettingMath.totalPool(stakes), sum(payouts));
	}

	@Test
	void rakeReducesEveryPayoutProportionally() {
		List<BettingMath.Stake> stakes = List.of(
			stake(1, 10, "50.00"),
			stake(2, 20, "50.00"));

		List<BettingMath.Payout> payouts = BettingMath.parimutuelPayouts(stakes, 10, new BigDecimal("10"));

		// total 100, rake 10, net 90 -> the single winner gets 90
		assertEquals(new BigDecimal("90.00"), payoutOf(payouts, 1));
	}

	@Test
	void returnsNothingWhenNobodyBackedTheWinner() {
		List<BettingMath.Stake> stakes = List.of(stake(1, 10, "10.00"));

		assertTrue(BettingMath.parimutuelPayouts(stakes, 20, NO_RAKE).isEmpty());
	}

	@Test
	void returnsNothingForAnEmptyPool() {
		assertTrue(BettingMath.parimutuelPayouts(List.of(), 10, NO_RAKE).isEmpty());
	}

	@Test
	void payoutOrderIsDeterministicForIdenticalRemainders() {
		List<BettingMath.Stake> stakes = List.of(
			stake(5, 10, "10.00"),
			stake(2, 10, "10.00"),
			stake(9, 10, "10.00"));

		List<BettingMath.Payout> first = BettingMath.parimutuelPayouts(stakes, 10, NO_RAKE);
		List<BettingMath.Payout> second = BettingMath.parimutuelPayouts(stakes, 10, NO_RAKE);

		// 30 / 3 = 10.00 exactly, so the leftover is zero and order does not matter
		assertEquals(sum(first), sum(second));
		assertEquals(new BigDecimal("30.00"), sum(first));
	}

	@Test
	void impliedOddsReflectThePool() {
		// total 100, option pool 25, no rake -> 4.00
		assertEquals(new BigDecimal("4.00"),
			BettingMath.impliedOdds(new BigDecimal("25.00"), new BigDecimal("100.00"), NO_RAKE));
		assertEquals(0, BettingMath.impliedOdds(BigDecimal.ZERO, new BigDecimal("100.00"), NO_RAKE)
			.compareTo(BigDecimal.ZERO));
	}

	// ------------------------------------------------------------------ fixed odds

	@Test
	void fixedOddsPayStakeTimesOdds() {
		assertEquals(new BigDecimal("25.00"),
			BettingMath.fixedOddsPayout(new BigDecimal("10.00"), new BigDecimal("2.50")));
	}

	@Test
	void fixedOddsTruncateDown() {
		// 3.33 * 1.11 = 3.6963 -> 3.69
		assertEquals(new BigDecimal("3.69"),
			BettingMath.fixedOddsPayout(new BigDecimal("3.33"), new BigDecimal("1.11")));
	}

	@Test
	void fixedOddsRejectNonsenseInput() {
		assertEquals(0, BettingMath.fixedOddsPayout(null, new BigDecimal("2")).compareTo(BigDecimal.ZERO));
		assertEquals(0, BettingMath.fixedOddsPayout(new BigDecimal("10"), null).compareTo(BigDecimal.ZERO));
		assertEquals(0, BettingMath.fixedOddsPayout(new BigDecimal("0"), new BigDecimal("2")).compareTo(BigDecimal.ZERO));
		assertEquals(0, BettingMath.fixedOddsPayout(new BigDecimal("10"), new BigDecimal("-2")).compareTo(BigDecimal.ZERO));
	}

	@Test
	void fixedOddsPayoutsUseTheFrozenOddsPerBet() {
		List<BettingMath.Stake> stakes = List.of(
			stake(1, 10, "10.00"),
			stake(2, 10, "10.00"),
			stake(3, 20, "10.00"));
		Map<Long, BigDecimal> odds = Map.of(1L, new BigDecimal("2.00"), 2L, new BigDecimal("3.00"),
			3L, new BigDecimal("5.00"));

		List<BettingMath.Payout> payouts = BettingMath.fixedOddsPayouts(stakes, 10, odds);

		assertEquals(new BigDecimal("20.00"), payoutOf(payouts, 1));
		assertEquals(new BigDecimal("30.00"), payoutOf(payouts, 2));
		assertEquals(2, payouts.size(), "only bets on the winning option are paid");
	}

	@Test
	void liabilityIsTheTotalTheCreatorWouldOwe() {
		List<BettingMath.Stake> stakes = List.of(
			stake(1, 10, "10.00"),
			stake(2, 10, "20.00"));
		Map<Long, BigDecimal> odds = Map.of(1L, new BigDecimal("2.00"), 2L, new BigDecimal("1.50"));

		assertEquals(new BigDecimal("50.00"), BettingMath.fixedOddsLiability(stakes, 10, odds));
		assertEquals(new BigDecimal("0.00"), BettingMath.fixedOddsLiability(stakes, 99, odds));
	}
}
