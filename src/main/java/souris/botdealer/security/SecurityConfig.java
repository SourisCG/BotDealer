/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import souris.botdealer.config.AppPaths;

/**
 * Chooses the credential store at startup: OS keychain first, AES-GCM encrypted file
 * as the fallback. The choice is logged (store name only, never a secret) so support
 * logs can tell which backend a user is on.
 *
 * <p>{@code botdealer.security.store} controls the behaviour:</p>
 * <ul>
 *   <li>{@code auto} (default) — probe the keychain, fall back to the encrypted file</li>
 *   <li>{@code keyring} — force the keychain (still falls back if unavailable)</li>
 *   <li>{@code file} — force the encrypted file (used by tests and rescue runs)</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	public SecretStore secretStore(
			@Value("${botdealer.security.store:auto}") String mode,
			@Value("${botdealer.data.dir:}") String configuredDataDir) {

		Path secretsDir = resolveSecretsDir(configuredDataDir);

		if ("file".equalsIgnoreCase(mode)) {
			log.info("Credential storage forced to the encrypted file store at {}", secretsDir);
			return new EncryptedFileSecretStore(secretsDir);
		}

		KeyringSecretStore keyring = new KeyringSecretStore();
		if (keyring.isAvailable()) {
			log.info("Credential storage: {}", keyring.displayName());
			return keyring;
		}

		log.warn("No usable OS keychain found; falling back to the AES-GCM encrypted file store at {}",
			secretsDir);
		return new EncryptedFileSecretStore(secretsDir);
	}

	private static Path resolveSecretsDir(String configuredDataDir) {
		if (configuredDataDir != null && !configuredDataDir.isBlank()) {
			return Path.of(configuredDataDir).resolve("secrets");
		}
		return AppPaths.secretsDir();
	}
}
