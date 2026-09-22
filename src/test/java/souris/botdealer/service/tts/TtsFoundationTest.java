/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import souris.botdealer.config.BundledAssets;
import souris.botdealer.util.WavWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Voice discovery and text cleaning — the two pieces that decide whether speech is
 * intelligible and whether the right model gets loaded.
 */
class TtsFoundationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	// ------------------------------------------------------------------ sanitizer

	private final TtsTextSanitizer sanitizer = new TtsTextSanitizer();

	@Test
	void removesMentionsAndCustomEmoji() {
		String cleaned = sanitizer.sanitize("<@123456> hello <@!789> <@&555> <#999> <:party:12345> world").text();

		assertEquals("hello world", cleaned);
	}

	@Test
	void removesAnimatedEmojiAndMarkdown() {
		assertEquals("nice one", sanitizer.sanitize("<a:dance:98765> **nice** _one_").text());
	}

	@Test
	void stripsLinksByDefault() {
		assertEquals("look at this", sanitizer.sanitize("look at this https://example.com/very/long/path").text());
		assertEquals("look at this", sanitizer.sanitize("look at this <https://example.com>").text());
	}

	@Test
	void canKeepLinksWhenAsked() {
		String cleaned = sanitizer.sanitize("go to https://example.com", 300, false).text();

		assertTrue(cleaned.contains("https://example.com"));
	}

	@Test
	void collapsesWhitespaceAndTrims() {
		assertEquals("one two three", sanitizer.sanitize("  one    two\n\n three  ").text());
	}

	@Test
	void reportsMessagesThatWouldSayNothing() {
		assertTrue(sanitizer.sanitize("<@123> <:x:1>").empty());
		assertTrue(sanitizer.sanitize("   ").empty());
		assertTrue(sanitizer.sanitize(null).empty());
		assertFalse(sanitizer.sanitize("hola").empty());
		assertFalse(sanitizer.isSpeakable("<@123>"));
		assertTrue(sanitizer.isSpeakable("hola"));
	}

	@Test
	void truncatesOnAWordBoundary() {
		String longText = "palabra ".repeat(100);

		String cleaned = sanitizer.sanitize(longText, 40, true).text();

		assertTrue(cleaned.length() <= 40);
		assertFalse(cleaned.endsWith(" "));
		assertFalse(cleaned.endsWith("palabr"), "must not cut a word in half: " + cleaned);
	}

	@Test
	void veryLongSingleWordIsCutToTheLimit() {
		String cleaned = sanitizer.sanitize("a".repeat(100), 20, true).text();

		assertEquals(20, cleaned.length());
	}

	// ------------------------------------------------------------------ wav writer

	@Test
	void writesAValidWavHeader(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("test.wav");
		short[] samples = new short[2205]; // 100 ms at 22050 Hz
		for (int index = 0; index < samples.length; index++) {
			samples[index] = (short) (Math.sin(index / 10.0) * 1000);
		}

		WavWriter.writeMono16(file, samples, 22050);

		byte[] bytes = Files.readAllBytes(file);
		assertEquals("RIFF", new String(bytes, 0, 4));
		assertEquals("WAVE", new String(bytes, 8, 4));
		assertEquals("fmt ", new String(bytes, 12, 4));
		assertEquals("data", new String(bytes, 36, 4));
		assertEquals(44 + samples.length * 2, bytes.length, "header plus 16-bit samples");
		// sample rate, little-endian, at offset 24
		int sampleRate = (bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16)
			| ((bytes[27] & 0xFF) << 24);
		assertEquals(22050, sampleRate);
		assertEquals(100, WavWriter.durationMillis(samples.length, 22050));
	}

	@Test
	void refusesToWriteSilence(@TempDir Path tempDir) {
		assertThrows(IOException.class, () -> WavWriter.writeMono16(tempDir.resolve("x.wav"), new short[0], 22050));
		assertThrows(IOException.class, () -> WavWriter.writeMono16(tempDir.resolve("x.wav"), new short[10], 0));
	}

	// ------------------------------------------------------------------ registry

	private static Path writeVoice(Path dir, String id, String languageCode, String languageName,
			int sampleRate, Map<String, Integer> speakers) throws IOException {
		Path model = dir.resolve(id + ".onnx");
		Files.writeString(model, "fake model");
		String speakerMap = speakers.isEmpty() ? "{}"
			: "{" + speakers.entrySet().stream()
				.map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
				.collect(java.util.stream.Collectors.joining(",")) + "}";
		String json = """
			{
			  "audio": { "sample_rate": %d },
			  "language": { "code": "%s", "name_native": "%s", "name_english": "%s" },
			  "num_speakers": %d,
			  "speaker_id_map": %s,
			  "inference": { "length_scale": 1.0, "noise_scale": 0.667, "noise_w": 0.8 }
			}
			""".formatted(sampleRate, languageCode, languageName, languageName, Math.max(1, speakers.size()),
			speakerMap);
		Files.writeString(dir.resolve(id + ".onnx.json"), json);
		return model;
	}

	@Test
	void scansVoicesAndReadsTheirMetadata(@TempDir Path tempDir) throws IOException {
		Path voices = tempDir.resolve("voices");
		Files.createDirectories(voices);
		writeVoice(voices, "es_ES-sharvard-medium", "es_ES", "Español", 22050,
			Map.of("M", 0, "F", 1));
		writeVoice(voices, "en_US-lessac-medium", "en_US", "English", 22050, Map.of());

		VoiceRegistry registry = new VoiceRegistry(new BundledAssets(), MAPPER, voices);
		var found = registry.refresh();

		assertEquals(2, found.size());
		var spanish = registry.find("es_ES-sharvard-medium").orElseThrow();
		assertEquals("es_ES", spanish.languageCode());
		assertEquals("Español", spanish.languageName());
		assertEquals(22050, spanish.sampleRate());
		assertTrue(spanish.isMultiSpeaker());
		assertEquals(2, spanish.speakerNames().size());
		assertEquals(0L, spanish.defaultSpeakerId());
		assertTrue(spanish.sizeBytes() > 0);

		var english = registry.find("en_US-lessac-medium").orElseThrow();
		assertFalse(english.isMultiSpeaker());
		assertEquals(0L, english.defaultSpeakerId());
	}

	@Test
	void prefersSpanishThenEnglishAsTheDefaultVoice(@TempDir Path tempDir) throws IOException {
		Path voices = tempDir.resolve("voices");
		Files.createDirectories(voices);
		writeVoice(voices, "de_DE-thorsten-medium", "de_DE", "Deutsch", 22050, Map.of());
		writeVoice(voices, "en_US-lessac-medium", "en_US", "English", 22050, Map.of());
		writeVoice(voices, "es_ES-sharvard-medium", "es_ES", "Español", 22050, Map.of());

		VoiceRegistry registry = new VoiceRegistry(new BundledAssets(), MAPPER, voices);

		assertEquals("es_ES-sharvard-medium", registry.defaultVoice().orElseThrow().id());
	}

	@Test
	void ignoresModelsWithoutAConfig(@TempDir Path tempDir) throws IOException {
		Path voices = tempDir.resolve("voices");
		Files.createDirectories(voices);
		Files.writeString(voices.resolve("orphan.onnx"), "model without config");
		writeVoice(voices, "en_US-lessac-medium", "en_US", "English", 22050, Map.of());

		VoiceRegistry registry = new VoiceRegistry(new BundledAssets(), MAPPER, voices);

		assertEquals(1, registry.refresh().size());
		assertTrue(registry.find("orphan").isEmpty());
	}

	@Test
	void survivesMalformedMetadata(@TempDir Path tempDir) throws IOException {
		Path voices = tempDir.resolve("voices");
		Files.createDirectories(voices);
		Files.writeString(voices.resolve("broken.onnx"), "model");
		Files.writeString(voices.resolve("broken.onnx.json"), "{ this is not json");

		VoiceRegistry registry = new VoiceRegistry(new BundledAssets(), MAPPER, voices);

		assertTrue(registry.refresh().isEmpty(), "a broken voice must be skipped, not fatal");
	}

	@Test
	void deletesAVoice(@TempDir Path tempDir) throws IOException {
		Path voices = tempDir.resolve("voices");
		Files.createDirectories(voices);
		writeVoice(voices, "en_US-lessac-medium", "en_US", "English", 22050, Map.of());
		VoiceRegistry registry = new VoiceRegistry(new BundledAssets(), MAPPER, voices);

		assertTrue(registry.delete("en_US-lessac-medium"));

		assertTrue(registry.voices().isEmpty());
		assertFalse(Files.exists(voices.resolve("en_US-lessac-medium.onnx")));
		assertFalse(Files.exists(voices.resolve("en_US-lessac-medium.onnx.json")));
		assertFalse(registry.delete("never-existed"));
	}

	@Test
	void anEmptyFolderIsNotAnError(@TempDir Path tempDir) {
		VoiceRegistry registry = new VoiceRegistry(new BundledAssets(), MAPPER, tempDir.resolve("missing"));

		assertTrue(registry.voices().isEmpty());
		assertTrue(registry.defaultVoice().isEmpty());
		assertTrue(registry.findAny().isEmpty());
		assertTrue(registry.find(null).isEmpty());
	}

	// ------------------------------------------------------------------ engine plumbing

	@Test
	void reportsUnavailableInsteadOfThrowingWhenVoicesAreMissing(@TempDir Path tempDir) {
		PiperTtsEngine engine = new PiperTtsEngine(MAPPER, tempDir.resolve("cache"));

		assertThrows(TtsSynthesisException.class, () -> engine.synthesize("hola", null, 1.0));
		assertThrows(TtsSynthesisException.class, () -> engine.synthesize("  ", null, 1.0));
		engine.shutdown();
	}

	@Test
	void clearingTheCacheRemovesLeftoverSpeech(@TempDir Path tempDir) throws IOException {
		Path cache = tempDir.resolve("cache");
		Files.createDirectories(cache);
		Files.writeString(cache.resolve("speech-1.wav"), "audio");
		PiperTtsEngine engine = new PiperTtsEngine(MAPPER, cache);

		assertEquals(1, engine.clearCache());

		assertTrue(engine.cacheDirectory().isPresent());
		engine.shutdown();
	}
}
