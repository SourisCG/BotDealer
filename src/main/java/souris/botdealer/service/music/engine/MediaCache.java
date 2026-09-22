/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import souris.botdealer.config.AppPaths;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

/**
 * LRU-ish media cache for audio downloaded by yt-dlp.
 *
 * <p>Downloading is the most reliable playback path, but it costs disk, so the cache is
 * capped (default 2 GB) and pruned oldest-first whenever something new is stored.</p>
 */
@Component
public class MediaCache {

	private static final Logger log = LoggerFactory.getLogger(MediaCache.class);

	private static final long BYTES_PER_MB = 1024L * 1024L;

	private final Path directory;
	private final AppSettingsService settings;

	@org.springframework.beans.factory.annotation.Autowired
	public MediaCache(AppSettingsService settings) {
		this(settings, AppPaths.dataDir());
	}

	/** Test seam: keeps the cache away from the real user data folder. */
	MediaCache(AppSettingsService settings, Path dataDir) {
		this.settings = settings;
		this.directory = dataDir.resolve("cache").resolve("media");
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			log.warn("Could not create the media cache at {}: {}", directory, e.toString());
		}
	}

	public Path dir() {
		return directory;
	}

	/** Builds the target path yt-dlp should write to. */
	public Path targetFor(String baseName, String extension) {
		return directory.resolve(baseName + "." + extension);
	}

	/** Marks a file as recently used so pruning keeps it longer. */
	public void record(Path file) {
		try {
			Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
		} catch (IOException e) {
			log.debug("Could not touch {}", file.getFileName());
		}
	}

	/** Total size of the cache in bytes. */
	public long size() {
		try (Stream<Path> files = Files.list(directory)) {
			return files.filter(Files::isRegularFile).mapToLong(MediaCache::lengthOf).sum();
		} catch (IOException e) {
			return 0L;
		}
	}

	/**
	 * Deletes the least recently used files until the cache fits the configured cap.
	 *
	 * @return how many files were removed
	 */
	public int prune() {
		long maxBytes = maxBytes();
		if (maxBytes <= 0) {
			return 0;
		}
		long total = size();
		if (total <= maxBytes) {
			return 0;
		}
		int removed = 0;
		try (Stream<Path> files = Files.list(directory)) {
			List<Path> oldestFirst = files.filter(Files::isRegularFile)
				.sorted(Comparator.comparingLong(MediaCache::lastModified))
				.toList();
			for (Path file : oldestFirst) {
				if (total <= maxBytes) {
					break;
				}
				long length = lengthOf(file);
				if (Files.deleteIfExists(file)) {
					total -= length;
					removed++;
				}
			}
		} catch (IOException e) {
			log.debug("Cache pruning stopped early: {}", e.toString());
		}
		if (removed > 0) {
			log.info("Pruned {} cached track(s), cache now {} MB", removed, total / BYTES_PER_MB);
		}
		return removed;
	}

	/** Removes every cached track (used by the application reset). */
	public int clear() {
		int removed = 0;
		try (Stream<Path> files = Files.list(directory)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				if (Files.deleteIfExists(file)) {
					removed++;
				}
			}
		} catch (IOException e) {
			log.debug("Could not fully clear the media cache: {}", e.toString());
		}
		return removed;
	}

	private long maxBytes() {
		int megabytes = settings.getInt(AppSettingKey.MUSIC_CACHE_MAX_MB);
		return Math.max(0, megabytes) * BYTES_PER_MB;
	}

	private static long lengthOf(Path file) {
		try {
			return Files.size(file);
		} catch (IOException e) {
			return 0L;
		}
	}

	private static long lastModified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}
}
