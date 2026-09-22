/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.settings.MusicEngine;
import souris.botdealer.util.Checksum;
import souris.botdealer.util.ProcessRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Engine plumbing: process execution, checksum verification, cache pruning, seeding and
 * the whole AUTO fallback matrix — all without yt-dlp, the network or a Discord session.
 */
class EngineLayerTest {

	// ------------------------------------------------------------------ process runner

	@Test
	void runsAProcessAndCapturesStdout() {
		ProcessRunner.Result result = ProcessRunner.run(
			List.of(javaBinary(), "--version"), Duration.ofSeconds(30));

		assertTrue(result.ok(), "java --version should succeed");
		assertFalse(result.stdout().isEmpty());
	}

	@Test
	void killsAProcessThatExceedsTheTimeout() {
		ProcessRunner.Result result = ProcessRunner.run(
			List.of(javaBinary(), "--version"), Duration.ofNanos(1));

		assertTrue(result.timedOut());
		assertFalse(result.ok());
	}

	@Test
	void reportsAMissingBinaryInsteadOfThrowing() {
		ProcessRunner.Result result = ProcessRunner.run(
			List.of("/definitely/not/a/binary"), Duration.ofSeconds(5));

		assertFalse(result.ok());
		assertTrue(result.stderr().contains("IOException"));
	}

	// ------------------------------------------------------------------ checksums

	@Test
	void verifiesAFileAgainstASha256SumsListing(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("yt-dlp");
		Files.writeString(file, "pretend binary");
		String hash = Checksum.sha256(file).orElseThrow();
		String sums = """
			# comment line
			aaaa  other-file
			%s  yt-dlp
			""".formatted(hash);

		assertEquals(hash, Checksum.expectedFor(sums, "yt-dlp").orElseThrow());
		assertTrue(Checksum.matches(file, hash));
		assertTrue(Checksum.expectedFor(sums, "missing-file").isEmpty());
	}

	@Test
	void rejectsATamperedDownload(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("yt-dlp");
		Files.writeString(file, "tampered");

		assertFalse(Checksum.matches(file, "0".repeat(64)));
	}

	@Test
	void parsesBothSha256SumFormats() {
		// Real listings look like "<64 hex>  ./name" or "<64 hex> *name".
		String sums = "a".repeat(64) + "  ./file-a\n" + "d".repeat(64) + " *file-b\n";

		assertEquals("a".repeat(64), Checksum.expectedFor(sums, "file-a").orElseThrow());
		assertEquals("d".repeat(64), Checksum.expectedFor(sums, "file-b").orElseThrow());
		// A short or malformed hash is not a checksum and must be ignored.
		assertTrue(Checksum.expectedFor("abc  file-c\n", "file-c").isEmpty());
	}

	// ------------------------------------------------------------------ media cache

	@Test
	void prunesTheOldestFilesWhenTheCacheIsFull(@TempDir Path tempDir) throws IOException {
		AppSettingsService settings = mock(AppSettingsService.class);
		when(settings.getInt(AppSettingKey.MUSIC_CACHE_MAX_MB)).thenReturn(1);
		MediaCache cache = new MediaCache(settings, tempDir);

		Path oldest = cache.dir().resolve("old.bin");
		Path newest = cache.dir().resolve("new.bin");
		Files.write(oldest, new byte[700 * 1024]);
		Files.write(newest, new byte[700 * 1024]);
		Files.setLastModifiedTime(oldest, java.nio.file.attribute.FileTime.fromMillis(1_000L));
		Files.setLastModifiedTime(newest, java.nio.file.attribute.FileTime.fromMillis(2_000_000L));

		int removed = cache.prune();

		assertEquals(1, removed, "only the oldest file has to go");
		assertFalse(Files.exists(oldest));
		assertTrue(Files.exists(newest));
		assertTrue(cache.size() <= 1024L * 1024L);
	}

	@Test
	void keepsEverythingWhenUnderTheCap(@TempDir Path tempDir) throws IOException {
		AppSettingsService settings = mock(AppSettingsService.class);
		when(settings.getInt(AppSettingKey.MUSIC_CACHE_MAX_MB)).thenReturn(10);
		MediaCache cache = new MediaCache(settings, tempDir);
		Files.write(cache.dir().resolve("track.bin"), new byte[1024]);

		assertEquals(0, cache.prune());
		assertEquals(1, cache.clear());
		assertEquals(0, cache.size());
	}

	// ------------------------------------------------------------------ installer

	@Test
	void seedsBundledEnginesOnlyOnce(@TempDir Path tempDir) throws IOException {
		Path dataDir = tempDir.resolve("data");
		Path bundle = tempDir.resolve("app/lib/app/engines");
		Files.createDirectories(bundle);
		Files.writeString(bundle.resolve("yt-dlp"), "binary");
		Files.createDirectories(bundle.resolve("ejs"));
		Files.writeString(bundle.resolve("ejs/solver.js"), "solver");

		String previous = System.getProperty("jpackage.app-path");
		System.setProperty("jpackage.app-path", tempDir.resolve("app/bin/BotDealer").toString());
		try {
			EngineInstaller installer = new EngineInstaller(dataDir);

			assertTrue(installer.hasBundledEngines());
			List<String> copied = installer.seedFromBundle();
			assertTrue(copied.contains("yt-dlp"));
			assertTrue(Files.isRegularFile(installer.ejsDir().resolve("solver.js")));
			assertTrue(installer.ytDlp().isPresent());

			// A second run must not overwrite an engine that may have been updated.
			Files.writeString(installer.ytDlp().orElseThrow(), "updated binary");
			assertTrue(installer.seedFromBundle().isEmpty());
			assertEquals("updated binary", Files.readString(installer.ytDlp().orElseThrow()));
		} finally {
			if (previous == null) {
				System.clearProperty("jpackage.app-path");
			} else {
				System.setProperty("jpackage.app-path", previous);
			}
		}
	}

	// ------------------------------------------------------------------ fallback matrix

	@Test
	void directModeOnlyUsesTheInProcessEngine() {
		FakeDirect direct = new FakeDirect(true, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(true);
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.YOUTUBE_DIRECT, direct, ytDlp).load("query", handler);

		assertEquals(1, direct.calls);
		assertEquals(0, ytDlp.resolveCalls, "yt-dlp must not be touched in direct mode");
		assertTrue(handler.trackLoaded);
	}

	@Test
	void autoModeDoesNotTouchYtDlpWhenDirectSucceeds() {
		FakeDirect direct = new FakeDirect(true, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(true);
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.AUTO, direct, ytDlp).load("query", handler);

		assertTrue(handler.trackLoaded);
		assertEquals(0, ytDlp.resolveCalls);
	}

	@Test
	void autoModeFallsBackWhenDirectFindsNothing() {
		// First the query finds nothing, then the resolved URL plays.
		FakeDirect direct = new FakeDirect(true, Outcome.NO_MATCHES, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(true);
		ytDlp.streamUrl = "https://stream.example/audio";
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.AUTO, direct, ytDlp).load("query", handler);

		assertEquals(1, ytDlp.resolveCalls);
		assertEquals("https://stream.example/audio", direct.lastIdentifier);
		assertTrue(handler.trackLoaded);
	}

	@Test
	void autoModeFallsBackWhenDirectFails() {
		FakeDirect direct = new FakeDirect(true, Outcome.LOAD_FAILED, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(true);
		ytDlp.streamUrl = "https://stream.example/audio";
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.AUTO, direct, ytDlp).load("query", handler);

		assertTrue(handler.trackLoaded);
	}

	@Test
	void downloadsIntoTheCacheWhenTheResolvedStreamCannotBePlayed() throws IOException {
		// The stream URL fails to play, so the router downloads and loads the file.
		FakeDirect direct = new FakeDirect(true, Outcome.LOAD_FAILED, Outcome.LOAD_FAILED, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(true);
		ytDlp.streamUrl = "https://stream.example/audio";
		Path downloaded = Files.createTempFile("track", ".webm");
		ytDlp.downloaded = downloaded;
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.AUTO, direct, ytDlp).load("https://youtu.be/dQw4w9WgXcQ", handler);

		assertEquals(1, ytDlp.downloadCalls, "the second attempt must download");
		assertEquals(downloaded.toAbsolutePath().toString(), direct.lastIdentifier,
			"the cached file is loaded through the local source");
		assertTrue(handler.trackLoaded);
	}

	@Test
	void ytDlpModeSkipsTheDirectEngineEntirely() {
		FakeDirect direct = new FakeDirect(true, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(true);
		ytDlp.streamUrl = "https://stream.example/audio";
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.YTDLP, direct, ytDlp).load("query", handler);

		assertEquals(1, ytDlp.resolveCalls);
		assertEquals(1, direct.calls, "only the resolved URL is handed to the player");
		assertEquals("https://stream.example/audio", direct.lastIdentifier);
	}

	@Test
	void reportsFailureWhenNoEngineIsAvailable() {
		FakeDirect direct = new FakeDirect(false, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(false);
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.AUTO, direct, ytDlp).load("query", handler);

		assertTrue(handler.loadFailed, "the caller must hear about it");
		assertEquals(0, ytDlp.resolveCalls);
	}

	@Test
	void reportsFailureWhenYtDlpCannotResolveAnything() {
		FakeDirect direct = new FakeDirect(true, Outcome.NO_MATCHES);
		FakeResolver ytDlp = new FakeResolver(true);
		ytDlp.streamUrl = null;
		ytDlp.downloaded = null;
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.AUTO, direct, ytDlp).load("query", handler);

		assertTrue(handler.noMatches);
	}

	@Test
	void reportsFailureWhenYtDlpIsMissingButRequired() {
		FakeDirect direct = new FakeDirect(true, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(false);
		RecordingHandler handler = new RecordingHandler();

		router(MusicEngine.YTDLP, direct, ytDlp).load("query", handler);

		assertTrue(handler.loadFailed);
	}

	@Test
	void engineStatusReflectsWhatIsAvailable() throws IOException {
		// The installer reports Deno from the file system, so give it a file to find.
		Files.createDirectories(tempDir.resolve("engines"));
		Files.writeString(tempDir.resolve("engines").resolve("deno"), "binary");
		FakeDirect direct = new FakeDirect(true, Outcome.TRACK);
		FakeResolver ytDlp = new FakeResolver(true);
		ytDlp.version = "2026.08.19";
		ytDlp.denoVersion = "deno 2.9.7";

		EngineStatus status = router(MusicEngine.AUTO, direct, ytDlp).status();

		assertTrue(status.directAvailable());
		assertTrue(status.ytDlpAvailable());
		assertTrue(status.denoAvailable());
		assertEquals("2026.08.19", status.ytDlpVersion());
		assertEquals("deno 2.9.7", status.denoVersion());
		assertEquals(MusicEngine.AUTO, status.mode());
		assertEquals("music.engine.status.both", status.summaryKey());
		assertTrue(status.anyAvailable());
	}

	@Test
	void engineStatusReportsWhenNothingIsUsable() {
		EngineStatus status = router(MusicEngine.AUTO, new FakeDirect(false, Outcome.TRACK),
			new FakeResolver(false)).status();

		assertFalse(status.anyAvailable());
		assertEquals("music.engine.status.none", status.summaryKey());
	}

	// ------------------------------------------------------------------ fixtures

	@TempDir
	Path tempDir;

	@SuppressWarnings("unchecked")
	private EngineRouter router(MusicEngine mode, DirectLoader direct, StreamResolver ytDlp) {
		AppSettingsService settings = mock(AppSettingsService.class);
		when(settings.get(AppSettingKey.MUSIC_ENGINE)).thenReturn(mode.name());
		when(settings.getInt(AppSettingKey.MUSIC_CACHE_MAX_MB)).thenReturn(2048);
		ObjectProvider<DirectLoader> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(direct);
		return new EngineRouter(settings, ytDlp, new EngineInstaller(tempDir), provider);
	}

	private static String javaBinary() {
		return Path.of(System.getProperty("java.home"), "bin", "java").toString();
	}

	private enum Outcome {
		TRACK,
		NO_MATCHES,
		LOAD_FAILED
	}

	/** Direct loader stub that replays a scripted outcome and records calls. */
	private static final class FakeDirect implements DirectLoader {

		private final boolean available;
		private final List<Outcome> script;
		private int calls;
		private String lastIdentifier;

		private FakeDirect(boolean available, Outcome... script) {
			this.available = available;
			this.script = List.of(script);
		}

		@Override
		public void load(String identifier, AudioLoadResultHandler handler) {
			int index = Math.min(calls, script.size() - 1);
			Outcome outcome = script.get(index);
			calls++;
			lastIdentifier = identifier;
			switch (outcome) {
				case TRACK -> handler.trackLoaded(mock(AudioTrack.class));
				case NO_MATCHES -> handler.noMatches();
				case LOAD_FAILED -> handler.loadFailed(
					new com.sedmelluq.discord.lavaplayer.tools.FriendlyException("boom",
						com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON,
						new IllegalStateException("boom")));
			}
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public List<String> sourceNames() {
			return List.of("youtube");
		}
	}

	/** yt-dlp stub: never spawns a process. */
	private static final class FakeResolver implements StreamResolver {

		private final boolean available;
		private String streamUrl;
		private Path downloaded;
		private String version = "";
		private String denoVersion = "";
		private int resolveCalls;
		private int downloadCalls;

		private FakeResolver(boolean available) {
			this.available = available;
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public java.util.Optional<String> resolveStreamUrl(String normalizedTarget) {
			resolveCalls++;
			return java.util.Optional.ofNullable(streamUrl);
		}

		@Override
		public java.util.Optional<Path> download(String normalizedTarget, String baseName) {
			downloadCalls++;
			return java.util.Optional.ofNullable(downloaded);
		}

		@Override
		public java.util.Optional<String> version() {
			return version.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(version);
		}

		@Override
		public java.util.Optional<String> denoVersion() {
			return denoVersion.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(denoVersion);
		}
	}

	/** Records which callback the router ended up calling. */
	private static final class RecordingHandler implements AudioLoadResultHandler {

		private boolean trackLoaded;
		private boolean playlistLoaded;
		private boolean noMatches;
		private boolean loadFailed;

		@Override
		public void trackLoaded(AudioTrack track) {
			trackLoaded = true;
		}

		@Override
		public void playlistLoaded(AudioPlaylist playlist) {
			playlistLoaded = true;
		}

		@Override
		public void noMatches() {
			noMatches = true;
		}

		@Override
		public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
			loadFailed = true;
		}
	}
}
