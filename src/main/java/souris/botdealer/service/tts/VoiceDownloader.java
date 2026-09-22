/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import souris.botdealer.net.HttpFetcher;
import souris.botdealer.util.Checksum;

/**
 * Downloads additional voices from the curated catalog.
 *
 * <p>Downloads land in temporary files and are only moved into the voices folder once
 * both the size and (when recorded) the SHA-256 match. A half-written model would make
 * the voice look installed while producing garbage, so nothing partial is ever published
 * under the final name.</p>
 *
 * <p>Progress is reported through a callback so the screen can show a bar, and a download
 * can be cancelled by id.</p>
 */
@Service
public class VoiceDownloader {

	private static final Logger log = LoggerFactory.getLogger(VoiceDownloader.class);

	/** Progress report for one voice. */
	public record Progress(String voiceId, Status status, long downloadedBytes, long totalBytes,
			String detail) {

		public enum Status {
			DOWNLOADING,
			VERIFYING,
			DONE,
			FAILED,
			CANCELLED,
			CHECKSUM_MISMATCH,
			SIZE_MISMATCH
		}

		public int percent() {
			if (totalBytes <= 0) {
				return status == Status.DONE ? 100 : 0;
			}
			return (int) Math.min(100, downloadedBytes * 100 / totalBytes);
		}
	}

	private final HttpFetcher fetcher;
	private final VoiceRegistry registry;
	private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

	public VoiceDownloader(HttpFetcher fetcher, VoiceRegistry registry) {
		this.fetcher = fetcher;
		this.registry = registry;
	}

	/**
	 * Downloads a voice and registers it.
	 *
	 * @param onProgress called as the download advances; may be null
	 * @return the installed voice, or empty when the download failed or was cancelled
	 */
	public Optional<VoiceDescriptor> download(VoiceCatalog.CatalogVoice voice, Consumer<Progress> onProgress) {
		AtomicBoolean cancelled = new AtomicBoolean(false);
		cancellations.put(voice.id(), cancelled);
		Path target = registry.voicesDir().resolve(voice.id());
		Path tempDir = null;
		try {
			Files.createDirectories(registry.voicesDir());
			tempDir = Files.createTempDirectory(registry.voicesDir(), ".download-" + voice.id());

			Path modelTemp = tempDir.resolve(voice.id() + ".onnx");
			Path configTemp = tempDir.resolve(voice.id() + ".onnx.json");

			report(onProgress, voice, Progress.Status.DOWNLOADING, 0, voice.sizeBytes(), "");
			fetcher.download(voice.modelUrl(), modelTemp);
			if (cancelled.get()) {
				return cancelled(voice, onProgress);
			}
			fetcher.download(voice.configUrl(), configTemp);
			if (cancelled.get()) {
				return cancelled(voice, onProgress);
			}

			report(onProgress, voice, Progress.Status.VERIFYING, voice.sizeBytes(), voice.sizeBytes(), "");
			Optional<Progress.Status> problem = verify(voice, modelTemp, configTemp);
			if (problem.isPresent()) {
				log.warn("Refusing to install voice {}: {}", voice.id(), problem.get());
				report(onProgress, voice, problem.get(), 0, voice.sizeBytes(), "");
				return Optional.empty();
			}

			Files.move(modelTemp, target.resolveSibling(voice.id() + ".onnx"),
				StandardCopyOption.REPLACE_EXISTING);
			Files.move(configTemp, target.resolveSibling(voice.id() + ".onnx.json"),
				StandardCopyOption.REPLACE_EXISTING);
			report(onProgress, voice, Progress.Status.DONE, voice.sizeBytes(), voice.sizeBytes(), "");
			log.info("Installed voice {}", voice.id());
			return registry.refresh().stream().filter(installed -> installed.id().equals(voice.id()))
				.findFirst();
		} catch (IOException e) {
			log.warn("Could not download voice {}: {}", voice.id(), e.toString());
			report(onProgress, voice, Progress.Status.FAILED, 0, voice.sizeBytes(), e.getClass().getSimpleName());
			return Optional.empty();
		} finally {
			cancellations.remove(voice.id());
			deleteQuietly(tempDir);
		}
	}

	/** Asks a running download to stop; it is checked between the two files. */
	public void cancel(String voiceId) {
		AtomicBoolean flag = cancellations.get(voiceId);
		if (flag != null) {
			flag.set(true);
		}
	}

	public boolean isDownloading(String voiceId) {
		return cancellations.containsKey(voiceId);
	}

	/** True when the voice is already installed. */
	public boolean isInstalled(VoiceCatalog.CatalogVoice voice) {
		return registry.find(voice.id()).isPresent();
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * @return the failure status when the download must be rejected, empty when it is fine
	 */
	private static Optional<Progress.Status> verify(VoiceCatalog.CatalogVoice voice, Path model, Path config) {
		long actualSize = sizeOf(model) + sizeOf(config);
		// The catalog records an approximate size, so allow a generous margin but still
		// catch a truncated or HTML-error download.
		if (voice.sizeBytes() > 0 && actualSize < voice.sizeBytes() / 2) {
			return Optional.of(Progress.Status.SIZE_MISMATCH);
		}
		if (!voice.hasChecksums()) {
			log.debug("Voice {} has no published checksum; verified by size only", voice.id());
			return Optional.empty();
		}
		if (!Checksum.matches(model, voice.sha256Model())) {
			return Optional.of(Progress.Status.CHECKSUM_MISMATCH);
		}
		if (!Checksum.matches(config, voice.sha256Config())) {
			return Optional.of(Progress.Status.CHECKSUM_MISMATCH);
		}
		return Optional.empty();
	}

	private Optional<VoiceDescriptor> cancelled(VoiceCatalog.CatalogVoice voice,
			Consumer<Progress> onProgress) {
		report(onProgress, voice, Progress.Status.CANCELLED, 0, voice.sizeBytes(), "");
		log.info("Voice download cancelled: {}", voice.id());
		return Optional.empty();
	}

	private static void report(Consumer<Progress> onProgress, VoiceCatalog.CatalogVoice voice,
			Progress.Status status, long downloaded, long total, String detail) {
		if (onProgress != null) {
			onProgress.accept(new Progress(voice.id(), status, downloaded, total, detail));
		}
	}

	private static long sizeOf(Path file) {
		try {
			return Files.size(file);
		} catch (IOException e) {
			return 0L;
		}
	}

	private static void deleteQuietly(Path directory) {
		if (directory == null) {
			return;
		}
		try (var files = Files.walk(directory)) {
			files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// best effort
				}
			});
		} catch (IOException ignored) {
			// best effort
		}
	}
}
