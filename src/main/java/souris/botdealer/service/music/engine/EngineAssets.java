/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.util.Locale;
import java.util.Optional;

/**
 * Which upstream release file belongs to this platform.
 *
 * <p>Shared by the runtime updater and the build-time asset fetcher so the installer and
 * the self-update path can never disagree about the file names.</p>
 */
public final class EngineAssets {

	public static final String YTDLP_RELEASE_API =
		"https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest";
	public static final String YTDLP_DOWNLOAD_BASE =
		"https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
	public static final String DENO_RELEASE_API =
		"https://api.github.com/repos/denoland/deno/releases/latest";

	private EngineAssets() {
	}

	public static Optional<String> ytDlpAssetName() {
		String os = os();
		String arch = arch();
		if (os.contains("win")) {
			return Optional.of("yt-dlp.exe");
		}
		if (os.contains("mac")) {
			return Optional.of("yt-dlp_macos");
		}
		if (os.contains("linux")) {
			return Optional.of(isArm(arch) ? "yt-dlp_linux_aarch64" : "yt-dlp_linux");
		}
		return Optional.empty();
	}

	public static Optional<String> denoAssetName() {
		String os = os();
		String arch = arch();
		if (os.contains("win")) {
			return Optional.of(isArm(arch) ? "deno-aarch64-pc-windows-msvc.zip"
				: "deno-x86_64-pc-windows-msvc.zip");
		}
		if (os.contains("mac")) {
			return Optional.of(isArm(arch) ? "deno-aarch64-apple-darwin.zip" : "deno-x86_64-apple-darwin.zip");
		}
		if (os.contains("linux")) {
			return Optional.of(isArm(arch) ? "deno-aarch64-unknown-linux-gnu.zip"
				: "deno-x86_64-unknown-linux-gnu.zip");
		}
		return Optional.empty();
	}

	public static String ytDlpDownloadUrl(String assetName) {
		return YTDLP_DOWNLOAD_BASE + assetName;
	}

	/** The Deno release JSON lists assets as name/download-url pairs. */
	public static Optional<String> denoDownloadUrl(String releaseJson, String assetName) {
		if (releaseJson == null || assetName == null) {
			return Optional.empty();
		}
		int nameIndex = releaseJson.indexOf("\"name\":\"" + assetName + "\"");
		if (nameIndex < 0) {
			return Optional.empty();
		}
		String marker = "\"browser_download_url\":\"";
		int urlIndex = releaseJson.indexOf(marker, nameIndex);
		if (urlIndex < 0) {
			return Optional.empty();
		}
		int start = urlIndex + marker.length();
		int end = releaseJson.indexOf('"', start);
		return end > start ? Optional.of(releaseJson.substring(start, end)) : Optional.empty();
	}

	/** The file name the binary should have once installed. */
	public static String ytDlpTargetName() {
		return isWindows() ? "yt-dlp.exe" : "yt-dlp";
	}

	public static String denoTargetName() {
		return isWindows() ? "deno.exe" : "deno";
	}

	public static boolean isWindows() {
		return os().contains("win");
	}

	private static String os() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	}

	private static String arch() {
		return System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
	}

	private static boolean isArm(String arch) {
		return arch.contains("aarch64") || arch.contains("arm");
	}
}
