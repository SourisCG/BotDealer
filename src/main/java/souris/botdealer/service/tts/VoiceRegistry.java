/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import souris.botdealer.config.AppPaths;
import souris.botdealer.config.BundledAssets;

/**
 * Knows which Piper voices are installed.
 *
 * <p>A voice is a pair of files: {@code <name>.onnx} and {@code <name>.onnx.json}. The
 * JSON carries what the app needs to use it correctly — sample rate (to write a valid
 * WAV), language, and the speaker table for multi-speaker models.</p>
 *
 * <p>Bundled voices are seeded from the installer into the writable data folder on first
 * scan, so a user can also add or delete voices there.</p>
 */
@Service
public class VoiceRegistry {

	private static final Logger log = LoggerFactory.getLogger(VoiceRegistry.class);

	private static final String MODEL_SUFFIX = ".onnx";
	private static final String CONFIG_SUFFIX = ".onnx.json";

	private final BundledAssets bundled;
	private final ObjectMapper mapper;
	private final Path voicesDir;

	private volatile List<VoiceDescriptor> cache;

	@org.springframework.beans.factory.annotation.Autowired
	public VoiceRegistry(BundledAssets bundled, ObjectMapper mapper) {
		this(bundled, mapper, AppPaths.dataDir().resolve("voices"));
	}

	/** Test seam: keeps the registry away from the real user data folder. */
	VoiceRegistry(BundledAssets bundled, ObjectMapper mapper, Path voicesDir) {
		this.bundled = bundled;
		this.mapper = mapper;
		this.voicesDir = voicesDir;
	}

	public Path voicesDir() {
		return voicesDir;
	}

	/** Seeds the bundled voices once, then lists everything installed. */
	public List<VoiceDescriptor> voices() {
		List<VoiceDescriptor> cached = cache;
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			if (cache == null) {
				bundled.seed("voices", voicesDir);
				cache = scan();
			}
			return cache;
		}
	}

	/** Forces a rescan, after a download or a manual file change. */
	public List<VoiceDescriptor> refresh() {
		synchronized (this) {
			cache = scan();
			return cache;
		}
	}

	public Optional<VoiceDescriptor> find(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		return voices().stream().filter(voice -> voice.id().equals(id)).findFirst();
	}

	/** The voice to use when the user has not chosen one: the first English or Spanish. */
	public Optional<VoiceDescriptor> defaultVoice() {
		return voices().stream()
			.sorted(Comparator.comparingInt(voice -> languageRank(voice.languageCode())))
			.findFirst();
	}

	public Optional<VoiceDescriptor> findAny() {
		return voices().stream().findFirst();
	}

	/** Deletes a voice's files. Refuses to remove nothing and reports whether it worked. */
	public boolean delete(String id) {
		Optional<VoiceDescriptor> voice = find(id);
		if (voice.isEmpty()) {
			return false;
		}
		boolean removed = false;
		try {
			removed = Files.deleteIfExists(voice.get().model());
			Files.deleteIfExists(voice.get().config());
		} catch (IOException e) {
			log.warn("Could not delete voice {}: {}", id, e.toString());
			return false;
		}
		refresh();
		return removed;
	}

	// ------------------------------------------------------------------ scanning

	private List<VoiceDescriptor> scan() {
		if (!Files.isDirectory(voicesDir)) {
			return List.of();
		}
		List<VoiceDescriptor> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(voicesDir)) {
			for (Path model : files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(MODEL_SUFFIX))
				.toList()) {
				Path config = model.resolveSibling(model.getFileName().toString() + ".json");
				if (!Files.isRegularFile(config)) {
					log.debug("Ignoring {} because its .onnx.json is missing", model.getFileName());
					continue;
				}
				parse(model, config).ifPresent(found::add);
			}
		} catch (IOException e) {
			log.warn("Could not scan the voices folder: {}", e.toString());
		}
		found.sort(Comparator.comparing(VoiceDescriptor::languageLabel)
			.thenComparing(VoiceDescriptor::displayName));
		log.info("Found {} installed voice(s)", found.size());
		return List.copyOf(found);
	}

	private Optional<VoiceDescriptor> parse(Path model, Path config) {
		try {
			JsonNode root = mapper.readTree(Files.readString(config));
			String id = model.getFileName().toString()
				.substring(0, model.getFileName().toString().length() - MODEL_SUFFIX.length());

			int sampleRate = root.path("audio").path("sample_rate").asInt(22050);
			JsonNode language = root.path("language");
			String languageCode = language.path("code").asText("und");
			String languageName = firstNonBlank(
				language.path("name_native").asText(""),
				language.path("name_english").asText(""),
				languageCode);

			Map<Long, String> speakers = new LinkedHashMap<>();
			JsonNode speakerMap = root.path("speaker_id_map");
			if (speakerMap.isObject()) {
				speakerMap.fields().forEachRemaining(entry ->
					speakers.put(entry.getValue().asLong(), entry.getKey()));
			}

			long size = Files.size(model) + Files.size(config);
			String displayName = firstNonBlank(language.path("name_english").asText(""),
				language.path("name_native").asText(""), id);

			return Optional.of(new VoiceDescriptor(id, displayName + " · " + qualitySuffix(id),
				languageCode, languageName, model, config, sampleRate, Map.copyOf(speakers), size));
		} catch (IOException | RuntimeException e) {
			log.warn("Could not read the voice metadata of {}: {}", config.getFileName(), e.toString());
			return Optional.empty();
		}
	}

	/** Appends the quality tier when the file name carries one (low/medium/high). */
	private static String qualitySuffix(String id) {
		for (String quality : List.of("x_low", "low", "medium", "high")) {
			if (id.endsWith("-" + quality)) {
				return quality;
			}
		}
		return "voice";
	}

	/** Spanish and English first, everything else after, for a sensible default. */
	private static int languageRank(String languageCode) {
		if (languageCode == null) {
			return 9;
		}
		String lower = languageCode.toLowerCase(java.util.Locale.ROOT);
		if (lower.startsWith("es")) {
			return 0;
		}
		if (lower.startsWith("en")) {
			return 1;
		}
		return 5;
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}
}
