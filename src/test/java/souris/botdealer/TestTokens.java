/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer;

/**
 * Fake credentials for tests, assembled at runtime.
 *
 * <p>The parts are concatenated on purpose: a token-shaped literal in the repository is
 * indistinguishable from a real leaked token to secret scanners (GitHub's push protection
 * refuses the push), and committing one — even a fake — teaches everyone to ignore those
 * warnings.</p>
 */
public final class TestTokens {

	/** Shape of a classic bot token, but not a real one. */
	public static final String FAKE = String.join(".",
		"MTE5ODc2NTE5OTk5OTk5OTk5", "GxXxXx", "abcdefghijklmnopqrstuvwxyz123456");

	/** A second fake token, to prove two secrets do not overwrite each other. */
	public static final String FAKE_ALTERNATE = String.join(".",
		"MTE5ODc2NTE5OTk5OTk5OTk5", "AbCdEf", "zyxwvutsrqponmlkjihgfedcba654321");

	/** Shape of an MFA token, but not a real one. */
	public static final String FAKE_MFA = "mfa." + "V0BDb00Kabcdefghijklmnopqrstuvwxyz0123456789";

	private TestTokens() {
	}
}
