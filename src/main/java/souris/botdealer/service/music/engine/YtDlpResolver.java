/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import souris.botdealer.util.ProcessRunner;

/**
 * Runs yt-dlp to resolve a stream URL or download the audio into the cache.
 *
 * <p>Blocking by design: callers run it on a background thread. Every invocation is
 * bounded by a timeout and its output is capped, so a hung or chatty yt-dlp cannot take
 * the application down.</p>
 */
@Component
public class YtDlpResolver implements StreamResolver {

	private static final Logger log = LoggerFactory.getLogger(YtDlpResolver.class);

	private static final Duration RESOLVE_TIMEOUT = Duration.ofSeconds(45);
	private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

	private final EngineInstaller installer;
	private final MediaCache cache;

	public YtDlpResolver(EngineInstaller installer, MediaCache cache) {
		this.installer = installer;
		this.cache = cache;
	}

	@Override
	public boolean isAvailable() {
		return installer.ytDlp().isPresent();
	}

	/**
	 * Resolves a direct audio stream URL without downloading.
	 *
	 * @param normalizedTarget output of {@link YtDlpTargets#normalize(String)}
	 */
	@Override
	public Optional<String> resolveStreamUrl(String normalizedTarget) {
		YtDlpPaths paths = installer.paths();
		if (!paths.hasYtDlp()) {
			return Optional.empty();
		}
		var result = ProcessRunner.run(
			YtDlpCommandBuilder.resolveStreamUrl(paths, normalizedTarget), RESOLVE_TIMEOUT);
		if (!result.ok()) {
			log.warn("yt-dlp could not resolve a stream (exit {}, timedOut {}): {}",
				result.exitCode(), result.timedOut(), firstStderrLine(result));
			return Optional.empty();
		}
		String url = result.firstLine();
		return url.startsWith("http") ? Optional.of(url) : Optional.empty();
	}

	/**
	 * Downloads the audio into the cache.
	 *
	 * @return the downloaded file, or empty when the download failed
	 */
	@Override
	public Optional<Path> download(String normalizedTarget, String baseName) {
		YtDlpPaths paths = installer.paths();
		if (!paths.hasYtDlp()) {
			return Optional.empty();
		}
		try {
			java.nio.file.Files.createDirectories(cache.dir());
		} catch (java.io.IOException e) {
			log.warn("Could not create the media cache: {}", e.toString());
			return Optional.empty();
		}
		var result = ProcessRunner.run(
			YtDlpCommandBuilder.downloadAudio(paths, normalizedTarget, baseName), DOWNLOAD_TIMEOUT);
		if (!result.ok()) {
			log.warn("yt-dlp download failed (exit {}, timedOut {}): {}",
				result.exitCode(), result.timedOut(), firstStderrLine(result));
			return Optional.empty();
		}
		// --print after_move:filepath writes the final path as the last line.
		Optional<Path> downloaded = result.stdout().reversed().stream()
			.filter(line -> !line.isBlank())
			.map(String::strip)
			.map(Path::of)
			.filter(Files::isRegularFile)
			.findFirst();
		downloaded.ifPresent(cache::record);
		cache.prune();
		return downloaded;
	}

	/** Reported yt-dlp version, for the engine card. */
	@Override
	public Optional<String> version() {
		return installer.ytDlp().flatMap(binary -> {
			var result = ProcessRunner.run(YtDlpCommandBuilder.version(binary), Duration.ofSeconds(20));
			String version = result.firstLine().strip();
			return result.ok() && !version.isBlank() ? Optional.of(version) : Optional.empty();
		});
	}

	/** Reported Deno version; empty when Deno is missing or broken. */
	@Override
	public Optional<String> denoVersion() {
		return installer.deno().flatMap(binary -> {
			var result = ProcessRunner.run(YtDlpCommandBuilder.denoVersion(binary), Duration.ofSeconds(20));
			if (!result.ok()) {
				return Optional.empty();
			}
			// `deno --version` prints "deno 2.x.y (...)" on the first line.
			String line = result.firstLine().strip();
			return line.isBlank() ? Optional.empty() : Optional.of(line);
		});
	}

	private static String firstStderrLine(ProcessRunner.Result result) {
		return result.stderr().lines().filter(line -> !line.isBlank()).findFirst().orElse("");
	}
}
