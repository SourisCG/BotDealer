/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.github.javakeyring.Keyring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OS keychain backed store: Windows Credential Manager, macOS Keychain, KDE KWallet
 * or the freedesktop Secret Service (gnome-keyring on most Linux desktops).
 *
 * <p>Two hard-won details:</p>
 * <ul>
 *   <li>Availability is probed with a canary entry instead of trusting
 *       {@code Keyring.create()}: a Linux box can have the libraries installed while no
 *       unlocked keyring daemon is running, in which case every write fails.</li>
 *   <li>The probe is bounded by a timeout and runs on a daemon virtual thread. A
 *       keyring waiting for an unlock prompt must never hang application startup; if it
 *       does not answer in time we simply use the encrypted file store.</li>
 * </ul>
 *
 * <p>Packaging note: the freedesktop backend needs
 * {@code com.sun.security.auth.module.UnixSystem}, which lives in the
 * {@code jdk.security.auth} module — it must be part of the jlink runtime or the
 * packaged app silently loses keychain support.</p>
 */
public class KeyringSecretStore implements SecretStore {

	private static final Logger log = LoggerFactory.getLogger(KeyringSecretStore.class);

	public static final String SERVICE_NAME = "BotDealer";
	private static final String CANARY_ACCOUNT = "__availability_probe__";
	private static final String CANARY_VALUE = "probe";
	private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

	private final Keyring keyring;
	private final boolean available;
	private final String unavailableReason;

	/** Creates a store and probes the backend within {@link #PROBE_TIMEOUT}. */
	public KeyringSecretStore() {
		FutureTask<Keyring> probe = new FutureTask<>(() -> {
			Keyring created = Keyring.create();
			if (!canaryRoundTrip(created)) {
				throw new SecretStoreException("canary round trip failed");
			}
			return created;
		});
		// Virtual threads are daemon threads, so an abandoned probe cannot keep the JVM alive.
		Thread.ofVirtual().name("keyring-probe").start(probe);

		Keyring created = null;
		String reason = "";
		try {
			created = probe.get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			probe.cancel(true);
			reason = "no answer within " + PROBE_TIMEOUT.toSeconds() + "s (locked or prompting keyring?)";
		} catch (ExecutionException e) {
			reason = rootCause(e).toString();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			reason = "interrupted";
		}

		this.keyring = created;
		this.available = created != null;
		this.unavailableReason = reason;
		if (!available) {
			log.warn("OS keychain unavailable ({}); using the encrypted file store instead", reason);
		}
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
		return available;
	}

	@Override
	public String unavailableReason() {
		return unavailableReason;
	}

	@Override
	public Optional<String> read(SecretKey key) {
		requireAvailable();
		try {
			String value = keyring.getPassword(SERVICE_NAME, key.storageId());
			return (value == null || value.isEmpty()) ? Optional.empty() : Optional.of(value);
		} catch (Exception e) {
			// Some backends signal "no such entry" with an exception.
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
		if (!available) {
			return;
		}
		try {
			keyring.deletePassword(SERVICE_NAME, key.storageId());
		} catch (Exception e) {
			log.debug("Keychain delete was a no-op for {}", key.storageId());
		}
	}

	private void requireAvailable() {
		if (!available) {
			throw new SecretStoreException("OS keychain is not available: " + unavailableReason);
		}
	}

	private static boolean canaryRoundTrip(Keyring keyring) {
		try {
			keyring.setPassword(SERVICE_NAME, CANARY_ACCOUNT, CANARY_VALUE);
			String readBack = keyring.getPassword(SERVICE_NAME, CANARY_ACCOUNT);
			return CANARY_VALUE.equals(readBack);
		} catch (Throwable t) {
			throw new SecretStoreException("canary probe failed: " + t, t);
		} finally {
			try {
				keyring.deletePassword(SERVICE_NAME, CANARY_ACCOUNT);
			} catch (Exception ignored) {
				// best effort cleanup
			}
		}
	}

	private static Throwable rootCause(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return current;
	}
}
