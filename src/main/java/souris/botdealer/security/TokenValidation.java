/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

/**
 * Outcome of validating a Discord token. {@code messageKey} is an i18n bundle key so
 * the UI can localize the explanation; no field ever contains the token.
 */
public record TokenValidation(Status status, BotIdentity identity, String messageKey, String detail) {

	public enum Status {
		VALID,
		MALFORMED,
		INVALID,
		RATE_LIMITED,
		NETWORK_ERROR,
		HTTP_ERROR
	}

	public static TokenValidation valid(BotIdentity identity) {
		return new TokenValidation(Status.VALID, identity, "wizard.token.result.valid", "");
	}

	public static TokenValidation malformed() {
		return new TokenValidation(Status.MALFORMED, null, "wizard.token.result.malformed", "");
	}

	public static TokenValidation invalid() {
		return new TokenValidation(Status.INVALID, null, "wizard.token.result.invalid", "");
	}

	public static TokenValidation rateLimited() {
		return new TokenValidation(Status.RATE_LIMITED, null, "wizard.token.result.rateLimited", "");
	}

	public static TokenValidation networkError(String detail) {
		return new TokenValidation(Status.NETWORK_ERROR, null, "wizard.token.result.network", detail);
	}

	public static TokenValidation httpError(int statusCode) {
		return new TokenValidation(Status.HTTP_ERROR, null, "wizard.token.result.http",
			Integer.toString(statusCode));
	}

	public boolean isValid() {
		return status == Status.VALID;
	}
}
