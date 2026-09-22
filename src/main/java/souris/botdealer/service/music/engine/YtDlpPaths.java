/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0. See LICENSE.
 */
package souris.botdealer.service.music.engine;

import java.nio.file.Path;

/**
 * Paths the yt-dlp invocations need. A {@code null} entry means "not available", and the
 * command builder then omits the corresponding flag.
 *
 * @param ytDlp    the yt-dlp executable
 * @param deno     the JavaScript runtime (required for full YouTube support since 2025)
 * @param ejsDir   directory holding the pre-bundled challenge solver
 * @param cacheDir where yt-dlp keeps its cache and downloaded audio
 */
public record YtDlpPaths(Path ytDlp, Path deno, Path ejsDir, Path cacheDir) {

	public boolean hasYtDlp() {
		return ytDlp != null;
	}

	public boolean hasDeno() {
		return deno != null;
	}
}
