/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import souris.botdealer.TestTokens;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretServiceTest {

	private final String token =
		TestTokens.FAKE;

	private InMemoryStore store;
	private SecretService service;

	@BeforeEach
	void setUp() {
		store = new InMemoryStore();
		service = new SecretService(store);
	}

	@Test
	void storesAndReadsBackASecret() {
		service.set(SecretKey.DISCORD_TOKEN, token);

		assertTrue(service.isSet(SecretKey.DISCORD_TOKEN));
		assertEquals(token, service.get(SecretKey.DISCORD_TOKEN).orElseThrow());
	}

	@Test
	void trimsAccidentalWhitespaceFromPastedTokens() {
		service.set(SecretKey.DISCORD_TOKEN, "  " + token + "\n");

		assertEquals(token, service.get(SecretKey.DISCORD_TOKEN).orElseThrow());
	}

	@Test
	void refusesToStoreEmptySecrets() {
		assertThrows(IllegalArgumentException.class, () -> service.set(SecretKey.DISCORD_TOKEN, "   "));
		assertFalse(store.writes > 0, "an empty secret must never reach the backing store");
	}

	@Test
	void exposesOnlyAMaskedValue() {
		service.set(SecretKey.DISCORD_TOKEN, token);

		String masked = service.masked(SecretKey.DISCORD_TOKEN);

		assertTrue(masked.endsWith(token.substring(token.length() - 4)));
		assertFalse(masked.contains(token.substring(0, 20)));
	}

	@Test
	void readingTwiceHitsTheCache() {
		service.set(SecretKey.DISCORD_TOKEN, token);
		service.get(SecretKey.DISCORD_TOKEN);
		int readsAfterFirstGet = store.reads;

		service.get(SecretKey.DISCORD_TOKEN);

		assertEquals(readsAfterFirstGet, store.reads);
	}

	@Test
	void resetDeletesEveryCredential() {
		service.set(SecretKey.DISCORD_TOKEN, token);
		service.set(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN, "refresh-value");

		service.removeAll();

		assertFalse(service.isSet(SecretKey.DISCORD_TOKEN));
		assertFalse(service.isSet(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN));
		assertTrue(store.values.isEmpty());
	}

	@Test
	void survivesAStoreThatThrowsOnRead() {
		store.failReads = true;

		assertTrue(service.get(SecretKey.DISCORD_TOKEN).isEmpty());
	}

	private static final class InMemoryStore implements SecretStore {

		private final Map<SecretKey, String> values = new EnumMap<>(SecretKey.class);
		private int reads;
		private int writes;
		private boolean failReads;

		@Override
		public String id() {
			return "in-memory";
		}

		@Override
		public String displayName() {
			return "In-memory test store";
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public Optional<String> read(SecretKey key) {
			reads++;
			if (failReads) {
				throw new SecretStoreException("simulated read failure");
			}
			return Optional.ofNullable(values.get(key));
		}

		@Override
		public void write(SecretKey key, String value) {
			writes++;
			values.put(key, value);
		}

		@Override
		public void delete(SecretKey key) {
			values.remove(key);
		}
	}
}
