/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

/**
 * Why a wallet balance changed. Every mutation writes exactly one ledger entry, so the
 * full history of a balance can always be reconstructed.
 */
public enum LedgerReason {

	STARTING_BALANCE("ledger.reason.startingBalance"),
	DAILY("ledger.reason.daily"),
	ADMIN_GRANT("ledger.reason.adminGrant"),
	ADMIN_REMOVE("ledger.reason.adminRemove"),
	BET_PLACED("ledger.reason.betPlaced"),
	BET_PAYOUT("ledger.reason.betPayout"),
	BET_REFUND("ledger.reason.betRefund"),
	RAKE("ledger.reason.rake");

	private final String labelKey;

	LedgerReason(String labelKey) {
		this.labelKey = labelKey;
	}

	public String labelKey() {
		return labelKey;
	}
}
