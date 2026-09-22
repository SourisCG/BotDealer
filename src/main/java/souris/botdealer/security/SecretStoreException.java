/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

/**
 * Thrown when a secret cannot be read from, or written to, any backing store.
 * Never includes the secret value itself.
 */
public class SecretStoreException extends RuntimeException {

	public SecretStoreException(String message) {
		super(message);
	}

	public SecretStoreException(String message, Throwable cause) {
		super(message, cause);
	}
}
