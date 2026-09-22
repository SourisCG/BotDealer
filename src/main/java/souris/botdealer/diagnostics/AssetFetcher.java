/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.diagnostics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import souris.botdealer.net.HttpFetcher;
import souris.botdealer.net.HttpFetcherImpl;
import souris.botdealer.service.music.engine.EngineAssets;
import souris.botdealer.service.music.engine.EngineInstaller;
import souris.botdealer.service.tts.VoiceCatalog;

/**
 * Build-time helper that fills the jpackage input with the assets the installers ship.
 *
 * <p>Run by the {@code package} Maven profile as
 * {@code AssetFetcher <target/app>}, it downloads the bundled Piper voices and the
 * yt-dlp/Deno binaries so the installer is self-contained. Reusing the application's own
 * download code keeps the build and the runtime in agreement about file names and
 * verification.</p>
 *
 * <p>Already-present files of the expected size are left alone, so re-running the build
 * (or a CI cache hit) costs nothing.</p>
 *
 * <p>It intentionally does not bundle the yt-dlp EJS solver: yt-dlp fetches that small
 * component from its own release page when the engine needs it, which is the officially
 * supported path.</p>
 */
public final class AssetFetcher {

	private static final ObjectMapperHolder MAPPER = new ObjectMapperHolder();

	private AssetFetcher() {
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("usage: AssetFetcher <jpackage-input-dir>");
			System.exit(2);
		}
		Path input = Path.of(args[0]);
		HttpFetcher fetcher = new HttpFetcherImpl();
		int failures = 0;

		try {
			failures += fetchVoices(fetcher, input.resolve("voices"));
			failures += fetchEngines(fetcher, input.resolve("engines"));
		} catch (Exception e) {
			System.err.println("Asset fetching failed: " + e);
			System.exit(1);
		}

		if (failures > 0) {
			System.err.println(failures + " asset(s) could not be prepared");
			System.exit(1);
		}
		System.out.println("Bundled assets are ready under " + input.toAbsolutePath());
	}

	// ------------------------------------------------------------------ voices

	private static int fetchVoices(HttpFetcher fetcher, Path voicesDir) throws IOException {
		Files.createDirectories(voicesDir);
		int failures = 0;
		List<VoiceCatalog.CatalogVoice> bundled = new VoiceCatalog(MAPPER.get()).bundled();
		if (bundled.isEmpty()) {
			System.err.println("The voice catalog lists no bundled voices");
			return 1;
		}
		for (VoiceCatalog.CatalogVoice voice : bundled) {
			failures += fetchVoiceFile(fetcher, voice.modelUrl(),
				voicesDir.resolve(voice.id() + ".onnx"), voice.sizeBytes());
			failures += fetchVoiceFile(fetcher, voice.configUrl(),
				voicesDir.resolve(voice.id() + ".onnx.json"), 0);
		}
		return failures;
	}

	private static int fetchVoiceFile(HttpFetcher fetcher, String url, Path target, long expectedSize) {
		try {
			if (isUsable(target, expectedSize)) {
				System.out.println("  cached  " + target.getFileName());
				return 0;
			}
			System.out.println("  fetching " + target.getFileName());
			fetcher.download(url, target);
			if (expectedSize > 0 && Files.size(target) < expectedSize / 2) {
				System.err.println("  truncated: " + target.getFileName());
				Files.deleteIfExists(target);
				return 1;
			}
			return 0;
		} catch (IOException e) {
			System.err.println("  failed: " + target.getFileName() + " (" + e.getMessage() + ")");
			return 1;
		}
	}

	// ------------------------------------------------------------------ engines

	private static int fetchEngines(HttpFetcher fetcher, Path enginesDir) throws IOException {
		Files.createDirectories(enginesDir);
		int failures = 0;

		var ytDlpAsset = EngineAssets.ytDlpAssetName();
		if (ytDlpAsset.isEmpty()) {
			System.err.println("No yt-dlp build is published for this platform");
			failures++;
		} else {
			Path target = enginesDir.resolve(EngineAssets.ytDlpTargetName());
			if (target.toFile().length() > 1_000_000) {
				System.out.println("  cached  " + target.getFileName());
			} else {
				System.out.println("  fetching " + ytDlpAsset.get());
				try {
					fetcher.download(EngineAssets.ytDlpDownloadUrl(ytDlpAsset.get()), target);
					EngineInstaller.makeExecutable(target);
				} catch (IOException e) {
					System.err.println("  failed: yt-dlp (" + e.getMessage() + ")");
					failures++;
				}
			}
		}

		var denoAsset = EngineAssets.denoAssetName();
		if (denoAsset.isEmpty()) {
			System.err.println("No Deno build is published for this platform");
			failures++;
			return failures;
		}
		Path denoTarget = enginesDir.resolve(EngineAssets.denoTargetName());
		if (denoTarget.toFile().length() > 1_000_000) {
			System.out.println("  cached  " + denoTarget.getFileName());
			return failures;
		}
		System.out.println("  fetching " + denoAsset.get());
		try {
			String releaseJson = fetcher.getText(EngineAssets.DENO_RELEASE_API);
			String url = EngineAssets.denoDownloadUrl(releaseJson, denoAsset.get()).orElse(null);
			if (url == null) {
				System.err.println("  Deno asset not found in the latest release");
				return failures + 1;
			}
			Path archive = Files.createTempDirectory("botdealer-deno").resolve(denoAsset.get());
			fetcher.download(url, archive);
			extractBinary(archive, denoTarget);
			EngineInstaller.makeExecutable(denoTarget);
		} catch (IOException e) {
			System.err.println("  failed: Deno (" + e.getMessage() + ")");
			failures++;
		}
		return failures;
	}

	/** Copies the {@code deno} entry out of the release zip. */
	private static void extractBinary(Path archive, Path target) throws IOException {
		String wanted = EngineAssets.denoTargetName();
		try (var in = Files.newInputStream(archive); var zip = new java.util.zip.ZipInputStream(in)) {
			java.util.zip.ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (!entry.isDirectory()
						&& java.nio.file.Path.of(entry.getName()).getFileName().toString().equals(wanted)) {
					Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
					return;
				}
			}
		}
		throw new IOException("the Deno archive did not contain " + wanted);
	}

	private static boolean isUsable(Path target, long expectedSize) {
		try {
			if (!Files.isRegularFile(target)) {
				return false;
			}
			return expectedSize <= 0 || Files.size(target) >= expectedSize / 2;
		} catch (IOException e) {
			return false;
		}
	}

	/** Lazily built ObjectMapper, so the class has no Spring dependency. */
	private static final class ObjectMapperHolder {

		private com.fasterxml.jackson.databind.ObjectMapper mapper;

		private com.fasterxml.jackson.databind.ObjectMapper get() {
			if (mapper == null) {
				mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
			}
			return mapper;
		}
	}
}
