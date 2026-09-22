/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import souris.botdealer.net.HttpFetcher;
import souris.botdealer.util.Checksum;
import souris.botdealer.util.ProcessRunner;

/**
 * Updates the external engine binaries in place.
 *
 * <p>This is the escape hatch for the day YouTube changes something: yt-dlp can be
 * refreshed from the app without waiting for a new BotDealer release.</p>
 *
 * <p>Safety rules, because a half-written engine would break music for everyone:</p>
 * <ol>
 *   <li>the download goes to a temporary file, never over the working binary;</li>
 *   <li>its SHA-256 is verified against the checksum published by the same release;</li>
 *   <li>the current binary is kept as {@code .previous} until the new one answers
 *       {@code --version}; if it does not, the previous one is restored.</li>
 * </ol>
 */
@Service
public class EngineUpdater {

	private static final Logger log = LoggerFactory.getLogger(EngineUpdater.class);

	/** Outcome of an update attempt, ready to be shown in the Music screen. */
	public record UpdateResult(Status status, String detail) {

		public enum Status {
			UPDATED,
			ALREADY_CURRENT,
			DOWNLOAD_FAILED,
			CHECKSUM_MISMATCH,
			BROKEN_BINARY,
			ROLLED_BACK,
			UNSUPPORTED_PLATFORM
		}

		public boolean updated() {
			return status == Status.UPDATED;
		}
	}

	private final HttpFetcher fetcher;
	private final EngineInstaller installer;

	public EngineUpdater(HttpFetcher fetcher, EngineInstaller installer) {
		this.fetcher = fetcher;
		this.installer = installer;
	}

	// ------------------------------------------------------------------ yt-dlp

	public UpdateResult updateYtDlp() {
		Optional<String> assetName = EngineAssets.ytDlpAssetName();
		if (assetName.isEmpty()) {
			return new UpdateResult(UpdateResult.Status.UNSUPPORTED_PLATFORM, System.getProperty("os.arch", ""));
		}
		Path target = installer.enginesDir().resolve(installer.ytDlpFileName());
		try {
			Path temp = Files.createTempDirectory("botdealer-update").resolve(assetName.get());
			fetcher.download(EngineAssets.ytDlpDownloadUrl(assetName.get()), temp);

			String sums = fetcher.getText(EngineAssets.YTDLP_DOWNLOAD_BASE + "SHA2-256SUMS");
			Optional<String> expected = Checksum.expectedFor(sums, assetName.get());
			if (expected.isEmpty()) {
				Files.deleteIfExists(temp);
				return new UpdateResult(UpdateResult.Status.CHECKSUM_MISMATCH,
					"no checksum published for " + assetName.get());
			}
			if (!Checksum.matches(temp, expected.get())) {
				Files.deleteIfExists(temp);
				log.warn("Refusing to install yt-dlp: checksum mismatch for {}", assetName.get());
				return new UpdateResult(UpdateResult.Status.CHECKSUM_MISMATCH, assetName.get());
			}

			EngineInstaller.makeExecutable(temp);
			return install(temp, target, UpdateResult.Status.DOWNLOAD_FAILED);
		} catch (IOException e) {
			log.warn("yt-dlp update failed: {}", e.toString());
			return new UpdateResult(UpdateResult.Status.DOWNLOAD_FAILED, e.getClass().getSimpleName());
		}
	}

	// ------------------------------------------------------------------ deno

	/**
	 * Deno ships as a zip. The archive is verified as a whole before the binary is
	 * extracted, because that is what the published checksum covers.
	 */
	public UpdateResult updateDeno() {
		Optional<String> assetName = EngineAssets.denoAssetName();
		if (assetName.isEmpty()) {
			return new UpdateResult(UpdateResult.Status.UNSUPPORTED_PLATFORM, System.getProperty("os.arch", ""));
		}
		Path target = installer.enginesDir().resolve(installer.denoFileName());
		try {
			Path tempDir = Files.createTempDirectory("botdealer-deno");
			Path archive = tempDir.resolve(assetName.get());
			String url = EngineAssets.denoDownloadUrl(fetcher.getText(EngineAssets.DENO_RELEASE_API), assetName.get())
				.orElse(null);
			if (url == null) {
				return new UpdateResult(UpdateResult.Status.DOWNLOAD_FAILED, "no asset url");
			}
			fetcher.download(url, archive);

			String expected = fetcher.getText(url + ".sha256sum").strip();
			if (!Checksum.matches(archive, expected)) {
				log.warn("Refusing to install Deno: checksum mismatch for {}", assetName.get());
				return new UpdateResult(UpdateResult.Status.CHECKSUM_MISMATCH, assetName.get());
			}

			Path extracted = extractDeno(archive, tempDir);
			if (extracted == null) {
				return new UpdateResult(UpdateResult.Status.DOWNLOAD_FAILED, "archive layout unexpected");
			}
			EngineInstaller.makeExecutable(extracted);
			return install(extracted, target, UpdateResult.Status.DOWNLOAD_FAILED);
		} catch (IOException e) {
			log.warn("Deno update failed: {}", e.toString());
			return new UpdateResult(UpdateResult.Status.DOWNLOAD_FAILED, e.getClass().getSimpleName());
		}
	}

	// ------------------------------------------------------------------ shared

	/**
	 * Swaps a verified binary into place, keeping the previous one until the new one is
	 * known to run.
	 */
	private UpdateResult install(Path candidate, Path target, UpdateResult.Status failureStatus) {
		Path backup = target.resolveSibling(target.getFileName() + ".previous");
		try {
			Files.createDirectories(target.getParent());
			if (Files.exists(target)) {
				Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
			}
			Files.move(candidate, target, StandardCopyOption.REPLACE_EXISTING);
			EngineInstaller.makeExecutable(target);

			if (runs(target)) {
				Files.deleteIfExists(backup);
				String version = probeVersion(target);
				log.info("Engine updated: {} ({})", target.getFileName(), version);
				return new UpdateResult(UpdateResult.Status.UPDATED, version);
			}

			log.warn("The downloaded engine does not run; restoring the previous one");
			if (Files.exists(backup)) {
				Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
				return new UpdateResult(UpdateResult.Status.ROLLED_BACK, target.getFileName().toString());
			}
			Files.deleteIfExists(target);
			return new UpdateResult(UpdateResult.Status.BROKEN_BINARY, target.getFileName().toString());
		} catch (IOException e) {
			log.warn("Could not install the engine: {}", e.toString());
			return new UpdateResult(failureStatus, e.getClass().getSimpleName());
		}
	}

	private static boolean runs(Path binary) {
		var result = ProcessRunner.run(java.util.List.of(binary.toString(), "--version"),
			Duration.ofSeconds(30));
		return result.ok();
	}

	private static String probeVersion(Path binary) {
		var result = ProcessRunner.run(java.util.List.of(binary.toString(), "--version"),
			Duration.ofSeconds(30));
		return result.firstLine().strip();
	}

	private static Path extractDeno(Path archive, Path targetDir) throws IOException {
		String wanted = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
			? "deno.exe"
			: "deno";
		try (InputStream in = Files.newInputStream(archive);
				ZipInputStream zip = new ZipInputStream(in)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				String name = Path.of(entry.getName()).getFileName().toString();
				if (name.equals(wanted)) {
					Path out = targetDir.resolve(wanted);
					Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
					return out;
				}
			}
		}
		return null;
	}
}
