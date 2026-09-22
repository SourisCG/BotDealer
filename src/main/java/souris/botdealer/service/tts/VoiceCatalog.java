/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The curated list of voices the app offers to download.
 *
 * <p>Shipping the list inside the app rather than querying Hugging Face at runtime means
 * the URLs are fixed and reviewable, and the screen works offline. Every entry carries the
 * expected size (always verified) and, where the build recorded it, the SHA-256 of both
 * files.</p>
 */
@Service
public class VoiceCatalog {

	private static final Logger log = LoggerFactory.getLogger(VoiceCatalog.class);

	private static final String RESOURCE = "/tts/voices-catalog.json";

	/**
	 * One downloadable voice.
	 *
	 * @param baseUrl     model URL without the extension; {@code .onnx} and
	 *                    {@code .onnx.json} are appended
	 * @param sha256Model expected hash, empty when the build did not record one
	 */
	public record CatalogVoice(String id, String displayName, String languageCode, String languageName,
			String baseUrl, long sizeBytes, boolean bundled, String sha256Model, String sha256Config) {

		public String modelUrl() {
			return baseUrl + ".onnx";
		}

		public String configUrl() {
			return baseUrl + ".onnx.json";
		}

		public boolean hasChecksums() {
			return sha256Model != null && !sha256Model.isBlank()
				&& sha256Config != null && !sha256Config.isBlank();
		}
	}

	private final List<CatalogVoice> voices;

	public VoiceCatalog(ObjectMapper mapper) {
		this.voices = load(mapper);
	}

	public List<CatalogVoice> voices() {
		return voices;
	}

	public Optional<CatalogVoice> find(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		return voices.stream().filter(voice -> voice.id().equals(id)).findFirst();
	}

	/** Voices that ship with the installer. */
	public List<CatalogVoice> bundled() {
		return voices.stream().filter(CatalogVoice::bundled).toList();
	}

	private static List<CatalogVoice> load(ObjectMapper mapper) {
		try (InputStream in = VoiceCatalog.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				log.warn("The voice catalog resource is missing: {}", RESOURCE);
				return List.of();
			}
			JsonNode root = mapper.readTree(in);
			return java.util.stream.StreamSupport.stream(root.path("voices").spliterator(), false)
				.map(VoiceCatalog::parse)
				.filter(java.util.Objects::nonNull)
				.toList();
		} catch (IOException e) {
			log.error("Could not read the voice catalog: {}", e.toString());
			return List.of();
		}
	}

	private static CatalogVoice parse(JsonNode node) {
		String id = node.path("id").asText("");
		String baseUrl = node.path("baseUrl").asText("");
		if (id.isBlank() || baseUrl.isBlank()) {
			log.warn("Skipping a catalog entry without id or baseUrl");
			return null;
		}
		return new CatalogVoice(id, node.path("displayName").asText(id),
			node.path("languageCode").asText("und"), node.path("languageName").asText(""),
			baseUrl, node.path("sizeBytes").asLong(0), node.path("bundled").asBoolean(false),
			node.path("sha256Model").asText(""), node.path("sha256Config").asText(""));
	}
}
