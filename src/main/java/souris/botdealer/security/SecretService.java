/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single entry point for reading and writing BotDealer credentials.
 *
 * <p>Wraps the active {@link SecretStore} (OS keychain or encrypted file) with a tiny
 * in-memory cache and never exposes a secret to logging: callers that need to show
 * something in the UI must use {@link TokenPatterns#maskForDisplay(String)}.</p>
 */
@Service
public class SecretService {

	private static final Logger log = LoggerFactory.getLogger(SecretService.class);

	private final SecretStore store;
	private final Map<SecretKey, String> cache = new EnumMap<>(SecretKey.class);

	public SecretService(SecretStore store) {
		this.store = store;
		log.info("Credential storage: {} ({})", store.displayName(), store.id());
	}

	public SecretStore store() {
		return store;
	}

	/** @return the secret if present and readable; empty otherwise */
	public synchronized Optional<String> get(SecretKey key) {
		String cached = cache.get(key);
		if (cached != null) {
			return Optional.of(cached);
		}
		try {
			Optional<String> value = store.read(key);
			value.ifPresent(v -> cache.put(key, v));
			return value;
		} catch (SecretStoreException e) {
			log.warn("Could not read credential '{}' from {}", key.storageId(), store.id());
			return Optional.empty();
		}
	}

	public synchronized void set(SecretKey key, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Refusing to store an empty secret: " + key.storageId());
		}
		store.write(key, value.strip());
		cache.put(key, value.strip());
	}

	public synchronized void remove(SecretKey key) {
		cache.remove(key);
		store.delete(key);
	}

	public synchronized boolean isSet(SecretKey key) {
		return get(key).isPresent();
	}

	/** Masked representation for the UI, e.g. {@code ••••1a2b}. */
	public synchronized String masked(SecretKey key) {
		return get(key).map(TokenPatterns::maskForDisplay).orElse("");
	}

	/** Drops every stored credential (used by the app reset flow). */
	public synchronized void removeAll() {
		for (SecretKey key : SecretKey.values()) {
			try {
				remove(key);
			} catch (SecretStoreException e) {
				log.warn("Could not delete credential '{}' during reset", key.storageId());
			}
		}
		cache.clear();
	}
}
