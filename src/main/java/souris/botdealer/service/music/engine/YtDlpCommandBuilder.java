/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

/**
 * Builds the yt-dlp argument lists.
 *
 * <p>Pure functions on purpose: the exact command line is a security boundary, so it is
 * worth testing without spawning a process. The rules it encodes:</p>
 * <ul>
 *   <li>arguments are a list, never a shell string, so nothing can be interpolated;</li>
 *   <li>{@code --} always precedes the target, so a query starting with {@code -} cannot
 *       be read as a flag;</li>
 *   <li>{@code --ignore-config} stops a user config from silently changing behaviour;</li>
 *   <li>clients that do not need a PO token are preferred, and the JS runtime plus the
 *       pre-bundled solver are passed explicitly instead of letting yt-dlp download
 *       remote components at runtime.</li>
 * </ul>
 */
public final class YtDlpCommandBuilder {

	/** Clients without a PO-token requirement, in preference order. */
	public static final String CLIENTS = "youtube:player_client=default,android_vr,web_safari";

	/** Audio-only format selector; WebM/Opus is what Discord wants. */
	public static final String FORMAT = "bestaudio[ext=webm]/bestaudio/best";

	/**
	 * Where yt-dlp gets its JavaScript challenge solver.
	 *
	 * <p>Since late 2025 yt-dlp needs a solver to answer YouTube's {@code n} challenge, and
	 * it only fetches it when asked. Blocking the fetch ({@code ejs:none}) leaves the engine
	 * able to play only the formats that need no challenge, so the official component is
	 * enabled instead: yt-dlp downloads it from its own release page and runs it inside the
	 * bundled Deno runtime. Documented in SECURITY.md.</p>
	 */
	public static final String REMOTE_COMPONENTS = "ejs:github";

	private YtDlpCommandBuilder() {
	}

	/** Common flags shared by every invocation. */
	private static void base(java.util.List<String> args, YtDlpPaths paths) {
		args.add("--ignore-config");
		args.add("--no-playlist");
		args.add("--no-progress");
		args.add("--no-colors");
		if (paths.cacheDir() != null) {
			args.add("--cache-dir");
			args.add(paths.cacheDir().toString());
		}
		if (paths.deno() != null) {
			args.add("--js-runtimes");
			args.add("deno:" + paths.deno());
		}
		if (paths.ejsDir() != null) {
			args.add("--plugin-dirs");
			args.add(paths.ejsDir().toString());
		}
		args.add("--remote-components");
		args.add(REMOTE_COMPONENTS);
	}

	/**
	 * Resolves the direct audio stream URL without downloading it.
	 *
	 * @param target a full URL or a {@code ytsearch1:} query, already normalized
	 */
	public static java.util.List<String> resolveStreamUrl(YtDlpPaths paths, String target) {
		java.util.List<String> args = new java.util.ArrayList<>();
		args.add(paths.ytDlp().toString());
		base(args, paths);
		args.add("-f");
		args.add(FORMAT);
		args.add("--extractor-args");
		args.add(CLIENTS);
		args.add("--get-url");
		args.add("--");
		args.add(target);
		return args;
	}

	/** Downloads the audio into the cache directory and prints the final file path. */
	public static java.util.List<String> downloadAudio(YtDlpPaths paths, String target, String baseName) {
		java.util.List<String> args = new java.util.ArrayList<>();
		args.add(paths.ytDlp().toString());
		base(args, paths);
		args.add("-f");
		args.add(FORMAT);
		args.add("--extractor-args");
		args.add(CLIENTS);
		args.add("-o");
		args.add(paths.cacheDir().resolve(baseName + ".%(ext)s").toString());
		args.add("--print");
		args.add("after_move:filepath");
		args.add("--");
		args.add(target);
		return args;
	}

	/** Prints the version; used to show it in the UI and to detect a broken binary. */
	public static java.util.List<String> version(java.nio.file.Path ytDlp) {
		return java.util.List.of(ytDlp.toString(), "--version", "--ignore-config");
	}

	/** Prints the Deno version. */
	public static java.util.List<String> denoVersion(java.nio.file.Path deno) {
		return java.util.List.of(deno.toString(), "--version");
	}
}
