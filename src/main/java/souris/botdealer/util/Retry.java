/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.util;

import java.util.function.Supplier;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Retries an operation that lost an optimistic-locking race.
 *
 * <p>Wallets carry a {@code @Version}; two near-simultaneous bets from the same user can
 * make one transaction fail. Callers must pass a supplier that runs in its own
 * transaction (see {@code WalletService}, which wraps it in a {@code TransactionTemplate}),
 * because retrying inside an already-rolled-back transaction is pointless.</p>
 */
public final class Retry {

	private static final Logger log = LoggerFactory.getLogger(Retry.class);

	private static final int DEFAULT_ATTEMPTS = 4;
	private static final long BASE_BACKOFF_MILLIS = 25;

	private Retry() {
	}

	public static <T> T onConflict(Supplier<T> action) {
		return onConflict(DEFAULT_ATTEMPTS, action);
	}

	public static <T> T onConflict(int maxAttempts, Supplier<T> action) {
		RuntimeException lastFailure = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return action.get();
			} catch (RuntimeException e) {
				if (!isConflict(e)) {
					throw e;
				}
				lastFailure = e;
				if (attempt < maxAttempts) {
					log.debug("Optimistic lock conflict, retrying ({}/{})", attempt, maxAttempts);
					sleep(BASE_BACKOFF_MILLIS * attempt);
				}
			}
		}
		throw lastFailure;
	}

	/** True when the failure (or any cause) is an optimistic locking conflict. */
	public static boolean isConflict(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof OptimisticLockingFailureException
					|| current instanceof OptimisticLockException) {
				return true;
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while retrying a conflicting transaction", e);
		}
	}
}
