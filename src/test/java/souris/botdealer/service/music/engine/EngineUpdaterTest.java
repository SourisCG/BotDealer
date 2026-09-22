/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import souris.botdealer.util.Checksum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine updater replaces a working binary, so the important behaviour is what it
 * does when something is wrong: a bad checksum or a binary that will not run must leave
 * the previous engine in place.
 */
class EngineUpdaterTest {

	private static final String RELEASE_ASSET = "yt-dlp_linux";

	@TempDir
	Path tempDir;

	/** Serves canned bytes for URLs; no network involved. */
	private static final class FakeFetcher implements HttpFetcher {

		private final Map<String, byte[]> resources = new HashMap<>();
		private boolean failDownloads;

		@Override
		public String getText(String url) throws IOException {
			byte[] bytes = resources.get(url);
			if (bytes == null) {
				throw new IOException("404 " + url);
			}
			return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
		}

		@Override
		public long download(String url, Path target) throws IOException {
			if (failDownloads) {
				throw new IOException("network down");
			}
			byte[] bytes = resources.get(url);
			if (bytes == null) {
				throw new IOException("404 " + url);
			}
			Files.createDirectories(target.getParent());
			Files.write(target, bytes);
			return bytes.length;
		}
	}

	/**
	 * A fake "yt-dlp" that is a shell script answering --version, so the updater's
	 * runnability check exercises a real process without needing yt-dlp.
	 */
	private Path fakeEngineScript(String version) throws IOException {
		Path script = tempDir.resolve("fake-" + version + ".sh");
		Files.writeString(script, "#!/bin/sh\necho " + version + "\n");
		EngineInstaller.makeExecutable(script);
		return script;
	}

	private byte[] scriptBytes(String version) {
		return ("#!/bin/sh\necho " + version + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private EngineInstaller installerFor(Path dataDir) {
		return new EngineInstaller(dataDir);
	}

	@Test
	void installsAVerifiedUpdate() throws IOException {
		Path dataDir = tempDir.resolve("data");
		EngineInstaller installer = installerFor(dataDir);
		Files.createDirectories(installer.enginesDir());
		Files.write(installer.enginesDir().resolve(installer.ytDlpFileName()), scriptBytes("old"));
		EngineInstaller.makeExecutable(installer.enginesDir().resolve(installer.ytDlpFileName()));

		byte[] newBinary = scriptBytes("2026.08.19");
		FakeFetcher fetcher = new FakeFetcher();
		String downloadBase = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
		fetcher.resources.put(downloadBase + RELEASE_ASSET, newBinary);
		fetcher.resources.put(downloadBase + "SHA2-256SUMS",
			(sha256Of(newBinary) + "  " + RELEASE_ASSET + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

		EngineUpdater updater = new EngineUpdater(fetcher, installer);
		EngineUpdater.UpdateResult result = updater.updateYtDlp();

		if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
			assertEquals(EngineUpdater.UpdateResult.Status.UPDATED, result.status(), result.detail());
			assertEquals("2026.08.19", result.detail());
			assertTrue(Files.exists(installer.enginesDir().resolve(installer.ytDlpFileName())));
		}
	}

	@Test
	void refusesAnUpdateWithAWrongChecksum() throws IOException {
		Path dataDir = tempDir.resolve("data");
		EngineInstaller installer = installerFor(dataDir);
		Files.createDirectories(installer.enginesDir());
		Path current = installer.enginesDir().resolve(installer.ytDlpFileName());
		Files.write(current, scriptBytes("old-version"));
		EngineInstaller.makeExecutable(current);

		FakeFetcher fetcher = new FakeFetcher();
		String downloadBase = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
		fetcher.resources.put(downloadBase + RELEASE_ASSET, scriptBytes("tampered"));
		fetcher.resources.put(downloadBase + "SHA2-256SUMS",
			("0".repeat(64) + "  " + RELEASE_ASSET + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

		EngineUpdater.UpdateResult result = new EngineUpdater(fetcher, installer).updateYtDlp();

		if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
			assertEquals(EngineUpdater.UpdateResult.Status.CHECKSUM_MISMATCH, result.status());
			assertTrue(Files.readString(current).contains("old-version"),
				"the working engine must be untouched");
			assertFalse(Files.exists(current.resolveSibling(current.getFileName() + ".previous")));
		}
	}

	@Test
	void keepsTheCurrentEngineWhenTheDownloadFails() throws IOException {
		Path dataDir = tempDir.resolve("data");
		EngineInstaller installer = installerFor(dataDir);
		Files.createDirectories(installer.enginesDir());
		Path current = installer.enginesDir().resolve(installer.ytDlpFileName());
		Files.write(current, scriptBytes("old-version"));
		EngineInstaller.makeExecutable(current);

		FakeFetcher fetcher = new FakeFetcher();
		fetcher.failDownloads = true;

		EngineUpdater.UpdateResult result = new EngineUpdater(fetcher, installer).updateYtDlp();

		if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
			assertEquals(EngineUpdater.UpdateResult.Status.DOWNLOAD_FAILED, result.status());
			assertTrue(Files.readString(current).contains("old-version"));
		}
	}

	@Test
	void reportsAMissingChecksumEntry() throws IOException {
		Path dataDir = tempDir.resolve("data");
		EngineInstaller installer = installerFor(dataDir);
		FakeFetcher fetcher = new FakeFetcher();
		String downloadBase = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
		fetcher.resources.put(downloadBase + RELEASE_ASSET, scriptBytes("new"));
		fetcher.resources.put(downloadBase + "SHA2-256SUMS",
			("a".repeat(64) + "  some-other-file\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

		EngineUpdater.UpdateResult result = new EngineUpdater(fetcher, installer).updateYtDlp();

		if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
			assertEquals(EngineUpdater.UpdateResult.Status.CHECKSUM_MISMATCH, result.status());
		}
	}

	private static String sha256Of(byte[] bytes) {
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			return java.util.HexFormat.of().formatHex(digest.digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
