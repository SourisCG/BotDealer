/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import souris.botdealer.service.betting.BetValidationException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input vetting for the yt-dlp engine. Everything here is a security boundary: a query
 * that slips through becomes a command-line argument.
 */
class YtDlpTargetsTest {

	@Test
	void plainTextBecomesASingleResultSearch() {
		assertEquals("ytsearch1:daft punk", YtDlpTargets.normalize("daft punk"));
		assertEquals("ytsearch1:spaced", YtDlpTargets.normalize("  spaced  "));
	}

	@Test
	void allowedYouTubeUrlsPassThrough() {
		assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
			YtDlpTargets.normalize("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
		assertEquals("https://youtu.be/dQw4w9WgXcQ", YtDlpTargets.normalize("https://youtu.be/dQw4w9WgXcQ"));
		assertEquals("https://music.youtube.com/watch?v=abc123", YtDlpTargets.normalize(
			"https://music.youtube.com/watch?v=abc123"));
	}

	@Test
	void rejectsOtherHostsAndSchemes() {
		// A non-YouTube host could stream anything; file:// would read the local disk.
		assertEquals("music.error.unsupportedHost", keyOf(
			assertThrows(BetValidationException.class,
				() -> YtDlpTargets.normalize("https://evil.example.com/audio"))));
		assertEquals("music.error.invalidQuery", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize("file:///etc/passwd"))));
		assertEquals("music.error.invalidQuery", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize("ftp://host/file"))));
	}

	@Test
	void rejectsFlagInjectionAndPaths() {
		// These would otherwise be read by yt-dlp as options, not as a search term.
		assertEquals("music.error.invalidQuery", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize("--exec=rm -rf /"))));
		assertEquals("music.error.invalidQuery", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize("-o/tmp/x"))));
		assertEquals("music.error.invalidQuery", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize("/etc/shadow"))));
	}

	@Test
	void rejectsControlCharactersAndAbsurdLength() {
		assertEquals("music.error.invalidQuery", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize("bad\nnewline"))));
		assertEquals("music.error.queryTooLong", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize("x".repeat(501)))));
	}

	@Test
	void rejectsEmptyInput() {
		assertEquals("music.error.emptyQuery", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize("   "))));
		assertEquals("music.error.emptyQuery", keyOf(
			assertThrows(BetValidationException.class, () -> YtDlpTargets.normalize(null))));
	}

	@Test
	void extractsVideoIdsForCacheNaming() {
		assertEquals("dQw4w9WgXcQ",
			YtDlpTargets.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10").orElseThrow());
		assertEquals("dQw4w9WgXcQ", YtDlpTargets.videoId("https://youtu.be/dQw4w9WgXcQ").orElseThrow());
		assertTrue(YtDlpTargets.videoId("https://www.youtube.com/playlist?list=PL123").isEmpty());
		assertTrue(YtDlpTargets.videoId("just a search").isEmpty());
	}

	@Test
	void detectsUrlsWithoutTrustingThem() {
		assertTrue(YtDlpTargets.looksLikeUrl("https://youtu.be/x"));
		assertTrue(YtDlpTargets.looksLikeUrl("HTTP://youtu.be/x"));
		assertTrue(!YtDlpTargets.looksLikeUrl("daft punk"));
	}

	@Test
	void commandAlwaysSeparatesOptionsFromTheTarget(@TempDir Path tempDir) {
		YtDlpPaths paths = new YtDlpPaths(tempDir.resolve("yt-dlp"), tempDir.resolve("deno"),
			tempDir.resolve("ejs"), tempDir);

		List<String> args = YtDlpCommandBuilder.resolveStreamUrl(paths, "ytsearch1:test");

		int separator = args.indexOf("--");
		assertTrue(separator > 0, "the -- separator must be present");
		assertEquals("ytsearch1:test", args.get(separator + 1), "the target must come after --");
		assertEquals(args.size() - 1, separator + 1, "nothing may follow the target");
	}

	@Test
	void commandDisablesUserConfigAndRemoteComponents(@TempDir Path tempDir) {
		YtDlpPaths paths = new YtDlpPaths(tempDir.resolve("yt-dlp"), tempDir.resolve("deno"),
			tempDir.resolve("ejs"), tempDir);

		List<String> args = YtDlpCommandBuilder.downloadAudio(paths, "ytsearch1:test", "abc");

		assertTrue(args.contains("--ignore-config"), "a user config must not change behaviour");
		assertTrue(args.contains("--remote-components"));
		assertEquals(YtDlpCommandBuilder.NO_REMOTE_COMPONENTS, args.get(args.indexOf("--remote-components") + 1),
			"yt-dlp must never fetch and execute remote components");
		assertTrue(args.contains("--js-runtimes"), "the bundled JS runtime must be passed explicitly");
		assertTrue(args.contains("--plugin-dirs"), "the pre-bundled solver must be passed explicitly");
		assertTrue(args.contains("--extractor-args"));
		assertEquals(YtDlpCommandBuilder.CLIENTS, args.get(args.indexOf("--extractor-args") + 1),
			"clients without a PO-token requirement must be preferred");
	}

	@Test
	void commandOmitsOptionalPathsWhenMissing(@TempDir Path tempDir) {
		YtDlpPaths paths = new YtDlpPaths(tempDir.resolve("yt-dlp"), null, null, tempDir);

		List<String> args = YtDlpCommandBuilder.resolveStreamUrl(paths, "ytsearch1:test");

		assertTrue(!args.contains("--js-runtimes"));
		assertTrue(!args.contains("--plugin-dirs"));
	}

	private static String keyOf(BetValidationException exception) {
		return exception.messageKey();
	}
}
