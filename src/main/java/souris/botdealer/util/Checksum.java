/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * SHA-256 helpers for verifying downloaded engine binaries.
 *
 * <p>Every engine update is checked against the checksum file published by the upstream
 * project before it replaces a working binary.</p>
 */
public final class Checksum {

	private Checksum() {
	}

	/** Hex SHA-256 of a file, or empty when it cannot be read. */
	public static Optional<String> sha256(Path file) {
		try (InputStream in = Files.newInputStream(file);
				DigestInputStream digest = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"))) {
			digest.transferTo(java.io.OutputStream.nullOutputStream());
			return Optional.of(HexFormat.of().formatHex(digest.getMessageDigest().digest()));
		} catch (IOException | NoSuchAlgorithmException e) {
			return Optional.empty();
		}
	}

	/**
	 * Finds the expected hash for a file name inside a {@code sha256sum}-style listing
	 * (as published by yt-dlp in {@code SHA2-256SUMS}).
	 */
	public static Optional<String> expectedFor(String checksumsText, String fileName) {
		if (checksumsText == null || fileName == null) {
			return Optional.empty();
		}
		for (String line : checksumsText.split("\\R")) {
			String trimmed = line.strip();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			// Format: "<hex>  <filename>" or "<hex> *<filename>"
			int separator = trimmed.indexOf(' ');
			if (separator <= 0) {
				continue;
			}
			String hash = trimmed.substring(0, separator).strip().toLowerCase(java.util.Locale.ROOT);
			String name = trimmed.substring(separator).strip();
			if (name.startsWith("*")) {
				name = name.substring(1);
			}
			name = name.replaceFirst("^\\./", "");
			if (name.equals(fileName) && hash.matches("[0-9a-f]{64}")) {
				return Optional.of(hash);
			}
		}
		return Optional.empty();
	}

	/** True when the file's SHA-256 matches the expected hex digest. */
	public static boolean matches(Path file, String expectedHex) {
		if (expectedHex == null || expectedHex.isBlank()) {
			return false;
		}
		return sha256(file)
			.map(actual -> actual.equalsIgnoreCase(expectedHex.strip()))
			.orElse(false);
	}
}
