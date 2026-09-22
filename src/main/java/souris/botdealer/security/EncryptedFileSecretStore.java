/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AES-256-GCM file store used when no OS keychain is available.
 *
 * <p>Each secret is one file under the app data folder:</p>
 * <pre>
 *   magic(4) | version(1) | salt(16) | iv(12) | ciphertext+tag
 * </pre>
 *
 * <p>The encryption key is derived with PBKDF2-HMAC-SHA256 (210k iterations) from a
 * machine/user-bound passphrase plus a random per-file salt. This protects secrets
 * at rest and keeps them out of the database, logs and backups that do not include
 * the user profile — but it is <em>not</em> protection against malware running as
 * the same user. On POSIX systems the files are chmod 600.</p>
 */
public class EncryptedFileSecretStore implements SecretStore {

	private static final Logger log = LoggerFactory.getLogger(EncryptedFileSecretStore.class);

	private static final byte[] MAGIC = {'B', 'D', 'S', '1'};
	private static final byte VERSION = 1;
	private static final int SALT_LENGTH = 16;
	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BITS = 256;
	private static final int PBKDF2_ITERATIONS = 210_000;
	private static final String FILE_SUFFIX = ".enc";

	private final Path secretsDir;
	private final SecureRandom random = new SecureRandom();
	private final byte[] saltSource;

	public EncryptedFileSecretStore(Path secretsDir) {
		this.secretsDir = secretsDir;
		this.saltSource = buildMachinePassphrase();
	}

	@Override
	public String id() {
		return "encrypted-file";
	}

	@Override
	public String displayName() {
		return "Encrypted file";
	}

	@Override
	public boolean isAvailable() {
		try {
			Files.createDirectories(secretsDir);
			return Files.isWritable(secretsDir);
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public Optional<String> read(SecretKey key) {
		Path file = fileFor(key);
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try {
			byte[] payload = Files.readAllBytes(file);
			int headerLength = MAGIC.length + 1 + SALT_LENGTH + IV_LENGTH;
			if (payload.length <= headerLength || !hasMagic(payload)) {
				throw new SecretStoreException("Corrupted secret file for " + key.storageId());
			}
			byte[] salt = Arrays.copyOfRange(payload, MAGIC.length + 1, MAGIC.length + 1 + SALT_LENGTH);
			byte[] iv = Arrays.copyOfRange(payload, MAGIC.length + 1 + SALT_LENGTH, headerLength);
			byte[] cipherText = Arrays.copyOfRange(payload, headerLength, payload.length);

			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), new GCMParameterSpec(TAG_BITS, iv));
			byte[] plain = cipher.doFinal(cipherText);
			try {
				return Optional.of(new String(plain, StandardCharsets.UTF_8));
			} finally {
				Arrays.fill(plain, (byte) 0);
			}
		} catch (SecretStoreException e) {
			throw e;
		} catch (Exception e) {
			throw new SecretStoreException("Cannot read the encrypted secret store", e);
		}
	}

	@Override
	public void write(SecretKey key, String value) {
		try {
			Files.createDirectories(secretsDir);
			byte[] salt = new byte[SALT_LENGTH];
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(salt);
			random.nextBytes(iv);

			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new GCMParameterSpec(TAG_BITS, iv));
			byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

			byte[] payload = new byte[MAGIC.length + 1 + SALT_LENGTH + IV_LENGTH + cipherText.length];
			int cursor = 0;
			System.arraycopy(MAGIC, 0, payload, cursor, MAGIC.length);
			cursor += MAGIC.length;
			payload[cursor++] = VERSION;
			System.arraycopy(salt, 0, payload, cursor, SALT_LENGTH);
			cursor += SALT_LENGTH;
			System.arraycopy(iv, 0, payload, cursor, IV_LENGTH);
			cursor += IV_LENGTH;
			System.arraycopy(cipherText, 0, payload, cursor, cipherText.length);

			writeAtomically(fileFor(key), payload);
			Arrays.fill(cipherText, (byte) 0);
		} catch (Exception e) {
			throw new SecretStoreException("Cannot write to the encrypted secret store", e);
		}
	}

	@Override
	public void delete(SecretKey key) {
		try {
			Files.deleteIfExists(fileFor(key));
		} catch (IOException e) {
			throw new SecretStoreException("Cannot delete the stored secret", e);
		}
	}

	/** Directory holding the encrypted files (used by the reset flow). */
	public Path directory() {
		return secretsDir;
	}

	private Path fileFor(SecretKey key) {
		return secretsDir.resolve(key.storageId() + FILE_SUFFIX);
	}

	private SecretKeySpec deriveKey(byte[] salt) throws Exception {
		byte[] passphrase = new byte[saltSource.length + salt.length];
		System.arraycopy(saltSource, 0, passphrase, 0, saltSource.length);
		System.arraycopy(salt, 0, passphrase, saltSource.length, salt.length);
		PBEKeySpec spec = new PBEKeySpec(toChars(passphrase), salt, PBKDF2_ITERATIONS, KEY_BITS);
		try {
			byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
				.generateSecret(spec)
				.getEncoded();
			try {
				return new SecretKeySpec(key, "AES");
			} finally {
				Arrays.fill(key, (byte) 0);
			}
		} finally {
			spec.clearPassword();
			Arrays.fill(passphrase, (byte) 0);
		}
	}

	private static void writeAtomically(Path target, byte[] payload) throws IOException {
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.write(temp, payload);
		restrictPermissions(temp);
		try {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException atomicUnsupported) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
		restrictPermissions(target);
	}

	private static void restrictPermissions(Path path) {
		try {
			Set<java.nio.file.attribute.PosixFilePermission> ownerOnly =
				PosixFilePermissions.fromString("rw-------");
			Files.setPosixFilePermissions(path, ownerOnly);
		} catch (UnsupportedOperationException | IOException e) {
			// Windows has no POSIX permissions; the user profile ACL applies instead.
			log.debug("POSIX permissions not applied to {}", path.getFileName());
		}
	}

	private static boolean hasMagic(byte[] payload) {
		for (int i = 0; i < MAGIC.length; i++) {
			if (payload[i] != MAGIC[i]) {
				return false;
			}
		}
		return true;
	}

	private static char[] toChars(byte[] bytes) {
		char[] chars = new char[bytes.length];
		for (int i = 0; i < bytes.length; i++) {
			chars[i] = (char) (bytes[i] & 0xFF);
		}
		return chars;
	}

	/**
	 * Binds the derived key to this machine and user account so copying the encrypted
	 * file to another machine is not enough to read it.
	 */
	private static byte[] buildMachinePassphrase() {
		String user = System.getProperty("user.name", "unknown");
		String host = hostName();
		String os = System.getProperty("os.name", "unknown");
		return (user + '\u0000' + host + '\u0000' + os + '\u0000' + "BotDealer-credentials-v1")
			.getBytes(StandardCharsets.UTF_8);
	}

	private static String hostName() {
		String env = System.getenv("COMPUTERNAME");
		if (env == null || env.isBlank()) {
			env = System.getenv("HOSTNAME");
		}
		if (env != null && !env.isBlank()) {
			return env;
		}
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (Exception e) {
			return "localhost";
		}
	}
}
