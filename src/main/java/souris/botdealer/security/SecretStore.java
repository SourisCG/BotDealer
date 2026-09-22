/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.util.Optional;

/**
 * Storage for BotDealer credentials.
 *
 * <p>Two implementations exist: the OS keychain (preferred) and an AES-256-GCM
 * encrypted file (fallback when no keyring daemon is available). Callers never
 * need to know which one is active — see {@link SecretService}.</p>
 *
 * <p>Implementations must never log, return or include secret values in
 * exception messages.</p>
 */
public interface SecretStore {

	/** Short machine-readable identifier, e.g. {@code keyring} or {@code encrypted-file}. */
	String id();

	/** Human-readable description for the Settings/About UI (localized by the caller). */
	String displayName();

	/** True when the store can currently persist secrets. */
	boolean isAvailable();

	/** @return the stored secret, or empty when nothing is stored under that key */
	Optional<String> read(SecretKey key);

	/** Stores or replaces a secret. */
	void write(SecretKey key, String value);

	/** Removes a secret. Deleting a missing secret is not an error. */
	void delete(SecretKey key);
}
