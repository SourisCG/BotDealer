/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.economy;

/**
 * Thrown when a withdrawal would leave a wallet below zero. Carries no sensitive data.
 */
public class InsufficientFundsException extends RuntimeException {

	private final String balance;
	private final String requested;

	public InsufficientFundsException(String balance, String requested) {
		super("Insufficient funds: balance " + balance + ", requested " + requested);
		this.balance = balance;
		this.requested = requested;
	}

	public String balance() {
		return balance;
	}

	public String requested() {
		return requested;
	}
}
