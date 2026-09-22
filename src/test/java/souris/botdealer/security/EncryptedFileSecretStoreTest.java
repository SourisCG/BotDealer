/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import souris.botdealer.TestTokens;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EncryptedFileSecretStoreTest {

	@TempDir
	Path tempDir;

	private final String token = TestTokens.FAKE;

	@Test
	void roundTripsASecret() {
		EncryptedFileSecretStore store = new EncryptedFileSecretStore(tempDir);

		store.write(SecretKey.DISCORD_TOKEN, token);

		assertEquals(token, store.read(SecretKey.DISCORD_TOKEN).orElseThrow());
		assertEquals("encrypted-file", store.id());
		assertTrue(store.isAvailable());
	}

	@Test
	void returnsEmptyWhenNothingStored() {
		EncryptedFileSecretStore store = new EncryptedFileSecretStore(tempDir);
		assertTrue(store.read(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN).isEmpty());
	}

	@Test
	void overwritesAnExistingSecret() {
		EncryptedFileSecretStore store = new EncryptedFileSecretStore(tempDir);
		String second = TestTokens.FAKE_ALTERNATE;

		store.write(SecretKey.DISCORD_TOKEN, token);
		store.write(SecretKey.DISCORD_TOKEN, second);

		assertEquals(second, store.read(SecretKey.DISCORD_TOKEN).orElseThrow());
	}

	@Test
	void deleteRemovesTheSecretAndItsFile() throws Exception {
		EncryptedFileSecretStore store = new EncryptedFileSecretStore(tempDir);
		store.write(SecretKey.DISCORD_TOKEN, token);

		assertTrue(Files.exists(tempDir.resolve("discord_token.enc")));

		store.delete(SecretKey.DISCORD_TOKEN);

		assertTrue(store.read(SecretKey.DISCORD_TOKEN).isEmpty());
		assertFalse(Files.exists(tempDir.resolve("discord_token.enc")));
	}

	@Test
	void keepsSecretsApart() {
		EncryptedFileSecretStore store = new EncryptedFileSecretStore(tempDir);
		store.write(SecretKey.DISCORD_TOKEN, token);
		store.write(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN, "refresh-token-value");

		assertEquals(token, store.read(SecretKey.DISCORD_TOKEN).orElseThrow());
		assertEquals("refresh-token-value", store.read(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN).orElseThrow());

		store.delete(SecretKey.DISCORD_TOKEN);
		assertEquals("refresh-token-value", store.read(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN).orElseThrow());
	}

	@Test
	void neverWritesPlaintextToDisk() throws Exception {
		EncryptedFileSecretStore store = new EncryptedFileSecretStore(tempDir);
		store.write(SecretKey.DISCORD_TOKEN, token);

		// ISO-8859-1 decodes bytes 1:1, so searching the raw file is meaningful.
		String raw = new String(Files.readAllBytes(tempDir.resolve("discord_token.enc")),
			StandardCharsets.ISO_8859_1);

		assertFalse(raw.contains(token), "the raw token leaked into the file");
		assertFalse(raw.contains(token.substring(0, 20)), "a token fragment leaked into the file");
	}

	@Test
	void detectsTampering() throws Exception {
		EncryptedFileSecretStore store = new EncryptedFileSecretStore(tempDir);
		store.write(SecretKey.DISCORD_TOKEN, token);

		Path file = tempDir.resolve("discord_token.enc");
		byte[] payload = Files.readAllBytes(file);
		payload[payload.length - 1] ^= 0x01;
		Files.write(file, payload);

		assertThrows(SecretStoreException.class, () -> store.read(SecretKey.DISCORD_TOKEN));
	}

	@Test
	void restrictsFilePermissionsOnPosixSystems() throws Exception {
		assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"));

		EncryptedFileSecretStore store = new EncryptedFileSecretStore(tempDir);
		store.write(SecretKey.DISCORD_TOKEN, token);

		var permissions = Files.getPosixFilePermissions(tempDir.resolve("discord_token.enc"));
		assertEquals(PosixFilePermissions.fromString("rw-------"), permissions);
	}
}
