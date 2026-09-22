/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import souris.botdealer.config.AppPaths;
import souris.botdealer.config.BundledAssets;

/**
 * Keeps the external engine binaries in the app data folder.
 *
 * <p>The installers ship yt-dlp, Deno and the challenge solver, but the application
 * image is read-only once installed ({@code /opt}, Program Files) and self-updating
 * needs a writable path. So on first use the bundled copies are <b>seeded</b> into
 * {@code <dataDir>/engines}, and that copy is what runs and what gets updated.</p>
 *
 * <p>When there is no bundle (development runs) the installer simply reports nothing
 * available and the engine card offers to download the binaries.</p>
 */
@Component
public class EngineInstaller {

	private static final Logger log = LoggerFactory.getLogger(EngineInstaller.class);

	private final Path enginesDir;
	private final Path cacheDir;
	private final BundledAssets bundled;

	@org.springframework.beans.factory.annotation.Autowired
	public EngineInstaller(BundledAssets bundled) {
		this(bundled, AppPaths.dataDir());
	}

	/** Test seam: keeps the installer away from the real user data folder. */
	EngineInstaller(BundledAssets bundled, Path dataDir) {
		this.bundled = bundled;
		this.enginesDir = dataDir.resolve("engines");
		this.cacheDir = dataDir.resolve("cache");
	}

	/** For tests that do not exercise seeding. */
	EngineInstaller(Path dataDir) {
		this(new BundledAssets(), dataDir);
	}

	public Path enginesDir() {
		return enginesDir;
	}

	/** Where the pre-bundled challenge solver lives once seeded. */
	public Path ejsDir() {
		return enginesDir.resolve("ejs");
	}

	/** Root of the media cache, also handed to yt-dlp as its cache directory. */
	public Path cacheDir() {
		return cacheDir;
	}

	public String ytDlpFileName() {
		return isWindows() ? "yt-dlp.exe" : "yt-dlp";
	}

	public String denoFileName() {
		return isWindows() ? "deno.exe" : "deno";
	}

	public Optional<Path> ytDlp() {
		return executable(enginesDir.resolve(ytDlpFileName()));
	}

	public Optional<Path> deno() {
		return executable(enginesDir.resolve(denoFileName()));
	}

	/** Paths bundle for the command builder; entries are null when missing. */
	public YtDlpPaths paths() {
		return new YtDlpPaths(ytDlp().orElse(null), deno().orElse(null),
			Files.isDirectory(ejsDir()) ? ejsDir() : null, cacheDir);
	}

	public boolean hasBundledEngines() {
		return bundled.directory("engines").isPresent();
	}

	/**
	 * Copies the bundled engines into the writable data folder, skipping files that are
	 * already there (an updated binary must never be overwritten by the bundled one).
	 *
	 * @return the file names that were copied
	 */
	public List<String> seedFromBundle() {
		return bundled.seed("engines", enginesDir);
	}

	/** True when the engine binaries look present (not that they run). */
	public boolean isSeeded() {
		return ytDlp().isPresent();
	}

	private Optional<Path> executable(Path path) {
		return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
	}

	/** Grants the executable bit on POSIX systems. */
	public static void makeExecutable(Path path) {
		try {
			path.toFile().setExecutable(true, true);
		} catch (SecurityException e) {
			log.debug("Could not set the executable bit on {}", path.getFileName());
		}
	}

	private static boolean isWindows() {
		return BundledAssets.isWindows();
	}
}
