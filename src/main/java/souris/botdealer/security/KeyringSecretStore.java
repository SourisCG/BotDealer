/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.util.Optional;

import com.github.javakeyring.Keyring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OS keychain backed store: Windows Credential Manager, macOS Keychain, KDE KWallet
 * or the freedesktop Secret Service (gnome-keyring on most Linux desktops).
 *
 * <p>Availability is probed once with a canary entry instead of trusting
 * {@code Keyring.create()}: a Linux box can have the libraries installed while no
 * unlocked keyring daemon is running, in which case every write fails.</p>
 */
public class KeyringSecretStore implements SecretStore {

	private static final Logger log = LoggerFactory.getLogger(KeyringSecretStore.class);

	public static final String SERVICE_NAME = "BotDealer";
	private static final String CANARY_ACCOUNT = "__availability_probe__";
	private static final String CANARY_VALUE = "probe";

	private final Keyring keyring;
	private final Boolean available;

	/** Creates a store and probes the backend. */
	public KeyringSecretStore() {
		Keyring created;
		boolean works = false;
		try {
			created = Keyring.create();
			works = probe(created);
		} catch (Throwable t) {
			// BackendNotSupportedException, UnsatisfiedLinkError, D-Bus failures...
			log.debug("OS keychain not usable: {}", t.toString());
			created = null;
		}
		this.keyring = created;
		this.available = works;
	}

	@Override
	public String id() {
		return "keyring";
	}

	@Override
	public String displayName() {
		return "OS keychain";
	}

	@Override
	public boolean isAvailable() {
		return Boolean.TRUE.equals(available);
	}

	@Override
	public Optional<String> read(SecretKey key) {
		requireAvailable();
		try {
			String value = keyring.getPassword(SERVICE_NAME, key.storageId());
			return (value == null || value.isEmpty()) ? Optional.empty() : Optional.of(value);
		} catch (Exception e) {
			// A missing entry surfaces as an exception in some backends.
			log.debug("Keychain read returned nothing for {}", key.storageId());
			return Optional.empty();
		}
	}

	@Override
	public void write(SecretKey key, String value) {
		requireAvailable();
		try {
			keyring.setPassword(SERVICE_NAME, key.storageId(), value);
		} catch (Exception e) {
			throw new SecretStoreException("Cannot write secret to the OS keychain", e);
		}
	}

	@Override
	public void delete(SecretKey key) {
		if (!isAvailable()) {
			return;
		}
		try {
			keyring.deletePassword(SERVICE_NAME, key.storageId());
		} catch (Exception e) {
			log.debug("Keychain delete was a no-op for {}", key.storageId());
		}
	}

	private void requireAvailable() {
		if (!isAvailable()) {
			throw new SecretStoreException("OS keychain is not available");
		}
	}

	private static boolean probe(Keyring keyring) {
		try {
			keyring.setPassword(SERVICE_NAME, CANARY_ACCOUNT, CANARY_VALUE);
			String readBack = keyring.getPassword(SERVICE_NAME, CANARY_ACCOUNT);
			keyring.deletePassword(SERVICE_NAME, CANARY_ACCOUNT);
			return CANARY_VALUE.equals(readBack);
		} catch (Throwable t) {
			log.debug("OS keychain canary probe failed: {}", t.toString());
			return false;
		}
	}
}
