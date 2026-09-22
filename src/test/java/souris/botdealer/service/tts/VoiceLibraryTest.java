/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import souris.botdealer.config.BundledAssets;
import souris.botdealer.net.HttpFetcher;
import souris.botdealer.util.Checksum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The voice catalog and its downloader.
 *
 * <p>The downloader publishes a voice into the voices folder, so the cases that matter
 * are the failures: a truncated file or a bad checksum must leave nothing behind that
 * looks installed.</p>
 */
class VoiceLibraryTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Serves canned bytes; no network. */
	private static class FakeFetcher implements HttpFetcher {

		private final java.util.Map<String, byte[]> resources = new java.util.HashMap<>();
		private boolean fail;

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
			if (fail) {
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

	private static final String CONFIG_JSON = """
		{ "audio": { "sample_rate": 22050 }, "language": { "code": "es_ES", "name_native": "Español" },
		  "num_speakers": 1, "speaker_id_map": {} }
		""";

	private static VoiceCatalog.CatalogVoice voice(String baseUrl, long size, String modelHash,
			String configHash) {
		return new VoiceCatalog.CatalogVoice("test-voice", "Test voice", "es_ES", "Español", baseUrl, size,
			false, modelHash, configHash);
	}

	private VoiceRegistry registryIn(Path voicesDir) {
		return new VoiceRegistry(new BundledAssets(), MAPPER, voicesDir);
	}

	@Test
	void theShippedCatalogIsValidAndCoversBothLanguages() {
		VoiceCatalog catalog = new VoiceCatalog(MAPPER);

		assertFalse(catalog.voices().isEmpty(), "the catalog resource must load");
		assertEquals(3, catalog.bundled().size(), "three voices ship in the installer");
		assertTrue(catalog.voices().stream().anyMatch(v -> v.languageCode().startsWith("es")));
		assertTrue(catalog.voices().stream().anyMatch(v -> v.languageCode().startsWith("en")));
		catalog.voices().forEach(v -> {
			assertTrue(v.baseUrl().startsWith("https://"), "catalog URLs must be https");
			assertTrue(v.modelUrl().endsWith(".onnx"));
			assertTrue(v.configUrl().endsWith(".onnx.json"));
		});
		assertTrue(catalog.find("es_ES-sharvard-medium").isPresent());
		assertTrue(catalog.find("does-not-exist").isEmpty());
		assertTrue(catalog.find(null).isEmpty());
	}

	@Test
	void installsAVerifiedVoice(@TempDir Path tempDir) throws IOException {
		Path voicesDir = tempDir.resolve("voices");
		VoiceRegistry registry = registryIn(voicesDir);
		byte[] model = "the model bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] config = CONFIG_JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8);

		FakeFetcher fetcher = new FakeFetcher();
		fetcher.resources.put("https://example.test/v.onnx", model);
		fetcher.resources.put("https://example.test/v.onnx.json", config);
		VoiceDownloader downloader = new VoiceDownloader(fetcher, registry);

		List<VoiceDownloader.Progress> progress = new ArrayList<>();
		var installed = downloader.download(voice("https://example.test/v", model.length + config.length,
			sha256(model), sha256(config)), progress::add);

		assertTrue(installed.isPresent());
		assertEquals("test-voice", installed.get().id());
		assertEquals(22050, installed.get().sampleRate());
		assertTrue(progress.stream().anyMatch(p -> p.status() == VoiceDownloader.Progress.Status.DONE));
		assertTrue(Files.exists(voicesDir.resolve("test-voice.onnx")));
		assertTrue(Files.exists(voicesDir.resolve("test-voice.onnx.json")));
		assertFalse(Files.exists(voicesDir.resolve(".download-test-voice")), "temp files must be cleaned up");
	}

	@Test
	void rejectsAWrongChecksumAndInstallsNothing(@TempDir Path tempDir) throws IOException {
		Path voicesDir = tempDir.resolve("voices");
		VoiceRegistry registry = registryIn(voicesDir);
		byte[] model = "tampered model".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] config = CONFIG_JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8);

		FakeFetcher fetcher = new FakeFetcher();
		fetcher.resources.put("https://example.test/v.onnx", model);
		fetcher.resources.put("https://example.test/v.onnx.json", config);
		VoiceDownloader downloader = new VoiceDownloader(fetcher, registry);

		var installed = downloader.download(voice("https://example.test/v", model.length + config.length,
			"0".repeat(64), sha256(config)), null);

		assertTrue(installed.isEmpty());
		assertTrue(registry.refresh().isEmpty(), "nothing may look installed");
		assertFalse(Files.exists(voicesDir.resolve("test-voice.onnx")));
	}

	@Test
	void rejectsATruncatedDownload(@TempDir Path tempDir) throws IOException {
		Path voicesDir = tempDir.resolve("voices");
		VoiceRegistry registry = registryIn(voicesDir);
		FakeFetcher fetcher = new FakeFetcher();
		fetcher.resources.put("https://example.test/v.onnx", "tiny".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		fetcher.resources.put("https://example.test/v.onnx.json",
			CONFIG_JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		VoiceDownloader downloader = new VoiceDownloader(fetcher, registry);

		// The catalog says 60 MB, the download produced a few bytes.
		var installed = downloader.download(voice("https://example.test/v", 60_000_000, "", ""), null);

		assertTrue(installed.isEmpty());
		assertTrue(registry.refresh().isEmpty());
	}

	@Test
	void reportsAFailedDownload(@TempDir Path tempDir) {
		VoiceRegistry registry = registryIn(tempDir.resolve("voices"));
		FakeFetcher fetcher = new FakeFetcher();
		fetcher.fail = true;
		VoiceDownloader downloader = new VoiceDownloader(fetcher, registry);

		List<VoiceDownloader.Progress> progress = new ArrayList<>();
		var installed = downloader.download(voice("https://example.test/v", 1000, "", ""), progress::add);

		assertTrue(installed.isEmpty());
		assertTrue(progress.stream().anyMatch(p -> p.status() == VoiceDownloader.Progress.Status.FAILED));
	}

	@Test
	void cancellingBetweenTheTwoFilesInstallsNothing(@TempDir Path tempDir) throws IOException {
		Path voicesDir = tempDir.resolve("voices");
		VoiceRegistry registry = registryIn(voicesDir);
		FakeFetcher fetcher = new FakeFetcher();
		fetcher.resources.put("https://example.test/v.onnx", "model".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		fetcher.resources.put("https://example.test/v.onnx.json",
			CONFIG_JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		VoiceDownloader downloader = new VoiceDownloader(fetcher, registry);

		var installed = downloader.download(voice("https://example.test/v", 0, "", ""), progress -> {
			if (progress.status() == VoiceDownloader.Progress.Status.DOWNLOADING) {
				downloader.cancel("test-voice");
			}
		});

		assertTrue(installed.isEmpty());
		assertTrue(registry.refresh().isEmpty());
	}

	@Test
	void progressReportsPercentages() {
		assertEquals(50, new VoiceDownloader.Progress("v", VoiceDownloader.Progress.Status.DOWNLOADING,
			50, 100, "").percent());
		assertEquals(100, new VoiceDownloader.Progress("v", VoiceDownloader.Progress.Status.DONE,
			0, 0, "").percent());
		assertEquals(0, new VoiceDownloader.Progress("v", VoiceDownloader.Progress.Status.DOWNLOADING,
			0, 0, "").percent());
	}

	private static String sha256(byte[] bytes) {
		try {
			return java.util.HexFormat.of().formatHex(
				java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
