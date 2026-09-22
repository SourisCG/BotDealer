/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.jvoiceproject.piperjni.PiperJNI;
import io.github.jvoiceproject.piperjni.PiperVoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import souris.botdealer.util.WavWriter;

/**
 * Offline speech synthesis with Piper.
 *
 * <p>Everything runs locally: the neural model, the phonemizer and ONNX Runtime are
 * bundled, so no text ever leaves the machine.</p>
 *
 * <p>Two constraints shape this class:</p>
 * <ul>
 *   <li>Piper is not thread-safe, so every call is serialized on a single worker thread
 *       and the caller blocks with a timeout instead of racing.</li>
 *   <li>Piper's speed is the model's {@code length_scale}, not a runtime argument. A
 *       derived config file is written per (voice, speed) and cached, which is how the
 *       wizard's speed setting is honoured.</li>
 * </ul>
 */
@Service
public class PiperTtsEngine {

	private static final Logger log = LoggerFactory.getLogger(PiperTtsEngine.class);

	private static final Duration SYNTHESIS_TIMEOUT = Duration.ofSeconds(90);
	private static final double MIN_SPEED = 0.5;
	private static final double MAX_SPEED = 2.0;

	private final ObjectMapper mapper;
	private final Path cacheDir;
	private final Path configCacheDir;

	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "piper-tts");
		thread.setDaemon(true);
		return thread;
	});

	/** Guarded by the worker thread. */
	private final Map<String, PiperVoice> voices = new HashMap<>();
	private PiperJNI piper;
	private boolean initialized;
	private String unavailableReason = "";

	@org.springframework.beans.factory.annotation.Autowired
	public PiperTtsEngine(ObjectMapper mapper) {
		this(mapper, souris.botdealer.config.AppPaths.dataDir().resolve("cache").resolve("tts"));
	}

	/** Test seam. */
	PiperTtsEngine(ObjectMapper mapper, Path cacheDir) {
		this.mapper = mapper;
		this.cacheDir = cacheDir;
		this.configCacheDir = cacheDir.resolve("config");
	}

	/** True when the native engine loaded. Checked lazily and cached. */
	public boolean isAvailable() {
		ensureInitialized();
		return initialized;
	}

	public String unavailableReason() {
		ensureInitialized();
		return unavailableReason;
	}

	/**
	 * Synthesizes text into a 16-bit mono WAV file.
	 *
	 * @param speed 1.0 is the voice's natural pace
	 * @return the written WAV file
	 * @throws TtsSynthesisException when the engine is unavailable or synthesis fails
	 */
	public Path synthesize(String text, VoiceDescriptor voice, double speed) {
		if (text == null || text.isBlank()) {
			throw new TtsSynthesisException("tts.error.emptyText");
		}
		if (voice == null) {
			throw new TtsSynthesisException("tts.error.noVoice");
		}
		ensureInitialized();
		if (!initialized) {
			throw new TtsSynthesisException("tts.error.engineUnavailable", unavailableReason);
		}

		double clamped = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed <= 0 ? 1.0 : speed));
		Future<Path> task = worker.submit(() -> render(text, voice, clamped));
		try {
			return task.get(SYNTHESIS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		} catch (java.util.concurrent.TimeoutException e) {
			task.cancel(true);
			throw new TtsSynthesisException("tts.error.timeout");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TtsSynthesisException("tts.error.interrupted");
		} catch (java.util.concurrent.ExecutionException e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			if (cause instanceof TtsSynthesisException synthesis) {
				throw synthesis;
			}
			log.warn("Synthesis failed: {}", cause.toString());
			throw new TtsSynthesisException("tts.error.failed", cause);
		}
	}

	/** Releases the native resources; called when the application shuts down. */
	public void shutdown() {
		worker.submit(() -> {
			voices.values().forEach(PiperVoice::close);
			voices.clear();
			if (piper != null) {
				piper.terminate();
				piper.close();
				piper = null;
			}
			initialized = false;
		});
		worker.shutdown();
	}

	// ------------------------------------------------------------------ internals

	private void ensureInitialized() {
		if (initialized || !unavailableReason.isEmpty()) {
			return;
		}
		synchronized (this) {
			if (initialized || !unavailableReason.isEmpty()) {
				return;
			}
			try {
				piper = new PiperJNI();
				piper.initialize();
				initialized = true;
				log.info("Piper {} ready", piper.getPiperVersion());
			} catch (Throwable t) {
				unavailableReason = t.getClass().getSimpleName()
					+ (t.getMessage() == null ? "" : ": " + t.getMessage());
				initialized = false;
				log.warn("Offline speech is unavailable on this platform: {}", unavailableReason);
			}
		}
	}

	/** Runs on the worker thread only. */
	private Path render(String text, VoiceDescriptor voice, double speed)
			throws IOException, PiperJNI.NotInitialized {
		PiperVoice loaded = loadVoice(voice, speed);
		short[] samples = piper.textToAudio(loaded, text);
		if (samples == null || samples.length == 0) {
			throw new TtsSynthesisException("tts.error.emptyAudio");
		}
		Path output = cacheDir.resolve("speech-" + System.nanoTime() + ".wav");
		WavWriter.writeMono16(output, samples, loaded.getSampleRate());
		log.debug("Synthesized {} samples ({}) for voice {}", samples.length, output.getFileName(), voice.id());
		return output;
	}

	/** Loads (and caches) a voice, deriving a config when the speed is not natural. */
	private PiperVoice loadVoice(VoiceDescriptor voice, double speed)
			throws IOException, PiperJNI.NotInitialized {
		String key = voice.id() + "@" + speed;
		PiperVoice cached = voices.get(key);
		if (cached != null) {
			return cached;
		}
		Path config = speed == 1.0 ? voice.config() : derivedConfig(voice, speed);
		PiperVoice loaded = piper.loadVoice(voice.model(), config, voice.defaultSpeakerId());
		voices.put(key, loaded);
		return loaded;
	}

	/**
	 * Writes a copy of the voice config with a different {@code length_scale}, which is
	 * Piper's way of changing speed. Cached per (voice, speed) so it happens once.
	 */
	private Path derivedConfig(VoiceDescriptor voice, double speed) throws IOException {
		Files.createDirectories(configCacheDir);
		Path target = configCacheDir.resolve(voice.id() + "-speed" + speed + ".json");
		if (Files.isRegularFile(target)) {
			return target;
		}
		ObjectNode root = (ObjectNode) mapper.readTree(Files.readString(voice.config()));
		ObjectNode inference = root.has("inference") && root.get("inference").isObject()
			? (ObjectNode) root.get("inference")
			: root.putObject("inference");
		// Piper multiplies the phoneme durations by length_scale, so a faster speed is a
		// smaller scale.
		inference.put("length_scale", 1.0 / speed);
		mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), root);
		log.debug("Derived a voice config for speed {}: {}", speed, target.getFileName());
		return target;
	}

	/** True when the engine can load this voice (model and config both readable). */
	public boolean canUse(VoiceDescriptor voice) {
		return voice != null && Files.isRegularFile(voice.model()) && Files.isRegularFile(voice.config());
	}

	/** Removes leftover WAV files, called when the app starts or a reset runs. */
	public int clearCache() {
		if (!Files.isDirectory(cacheDir)) {
			return 0;
		}
		AtomicBoolean failed = new AtomicBoolean(false);
		try (var files = Files.walk(cacheDir)) {
			int removed = 0;
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				try {
					if (Files.deleteIfExists(file)) {
						removed++;
					}
				} catch (IOException e) {
					failed.set(true);
				}
			}
			return removed;
		} catch (IOException e) {
			return 0;
		}
	}

	public Optional<Path> cacheDirectory() {
		return Optional.of(cacheDir);
	}
}
