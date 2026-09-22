/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.util;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyAndRetryTest {

	// ------------------------------------------------------------------ money

	@Test
	void normalizesUserInputHalfUp() {
		assertEquals(new BigDecimal("10.00"), Money.normalize(new BigDecimal("10")));
		assertEquals(new BigDecimal("10.01"), Money.normalize(new BigDecimal("10.005")));
		assertEquals(new BigDecimal("10.00"), Money.normalize(new BigDecimal("9.999")));
		assertEquals(Money.ZERO, Money.normalize(null));
	}

	@Test
	void floorsPayoutsSoRoundingNeverCreatesMoney() {
		assertEquals(new BigDecimal("0.33"), Money.floor(new BigDecimal("0.339")));
		assertEquals(new BigDecimal("0.33"), Money.floor(new BigDecimal("1").divide(new BigDecimal("3"), 10,
			java.math.RoundingMode.HALF_UP)));
		// DOWN truncates toward zero; payouts are always positive so it equals a floor there.
		assertEquals(new BigDecimal("-0.33"), Money.floor(new BigDecimal("-0.339")));
	}

	@Test
	void positivityChecks() {
		assertTrue(Money.isPositive(new BigDecimal("0.01")));
		assertFalse(Money.isPositive(BigDecimal.ZERO));
		assertFalse(Money.isPositive(null));
		assertTrue(Money.isZeroOrPositive(BigDecimal.ZERO));
		assertFalse(Money.isZeroOrPositive(new BigDecimal("-0.01")));
	}

	// ------------------------------------------------------------------ retry

	@Test
	void retriesUntilTheConflictDisappears() {
		int[] attempts = {0};

		String result = Retry.onConflict(4, () -> {
			attempts[0]++;
			if (attempts[0] < 3) {
				throw new OptimisticLockingFailureException("conflict");
			}
			return "ok";
		});

		assertEquals("ok", result);
		assertEquals(3, attempts[0]);
	}

	@Test
	void givesUpAfterTheConfiguredAttempts() {
		int[] attempts = {0};

		assertThrows(OptimisticLockingFailureException.class, () -> Retry.onConflict(3, () -> {
			attempts[0]++;
			throw new OptimisticLockingFailureException("always conflicting");
		}));

		assertEquals(3, attempts[0]);
	}

	@Test
	void doesNotRetryUnrelatedFailures() {
		int[] attempts = {0};

		assertThrows(IllegalStateException.class, () -> Retry.onConflict(4, () -> {
			attempts[0]++;
			throw new IllegalStateException("not a conflict");
		}));

		assertEquals(1, attempts[0]);
	}

	@Test
	void detectsConflictsWrappedInsideOtherExceptions() {
		assertTrue(Retry.isConflict(
			new RuntimeException("outer", new OptimisticLockingFailureException("inner"))));
		assertFalse(Retry.isConflict(new RuntimeException("plain")));
	}
}
