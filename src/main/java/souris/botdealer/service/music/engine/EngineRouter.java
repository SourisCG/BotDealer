/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

import java.util.Optional;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.settings.MusicEngine;

/**
 * Decides how a request is resolved, and implements the AUTO fallback.
 *
 * <pre>
 *   YOUTUBE_DIRECT : in-process only
 *   YTDLP          : yt-dlp only (stream URL, then a cached download)
 *   AUTO           : in-process first; on failure, yt-dlp; if the stream cannot be
 *                    played, download into the cache and play the file
 * </pre>
 *
 * <p>The fallback runs on the LavaPlayer loader thread that reported the failure, which
 * is a background thread; a blocking yt-dlp call there is acceptable and keeps the
 * orchestration in one place.</p>
 */
@Service
public class EngineRouter {

	private static final Logger log = LoggerFactory.getLogger(EngineRouter.class);

	private final AppSettingsService settings;
	private final StreamResolver ytDlp;
	private final EngineInstaller installer;
	private final ObjectProvider<DirectLoader> directLoader;

	public EngineRouter(AppSettingsService settings, StreamResolver ytDlp, EngineInstaller installer,
			ObjectProvider<DirectLoader> directLoader) {
		this.settings = settings;
		this.ytDlp = ytDlp;
		this.installer = installer;
		this.directLoader = directLoader;
	}

	/** The engine chosen in Settings. */
	public MusicEngine mode() {
		return MusicEngine.fromStored(settings.get(AppSettingKey.MUSIC_ENGINE));
	}

	/** Whether the in-process engine is registered (the playback layer provides it). */
	public boolean isDirectAvailable() {
		DirectLoader loader = directLoader.getIfAvailable();
		return loader != null && loader.isAvailable();
	}

	/** Current capabilities, for the Music screen. */
	public EngineStatus status() {
		Optional<String> ytDlpVersion = ytDlp.version();
		return new EngineStatus(mode(), isDirectAvailable(), ytDlpVersion.isPresent(),
			installer.deno().isPresent(), ytDlpVersion.orElse(""),
			ytDlp.denoVersion().orElse(""), installer.enginesDir());
	}

	/**
	 * Loads a request, applying the configured engine and the AUTO fallback.
	 *
	 * @param query user input: a YouTube URL or a search phrase
	 */
	public void load(String query, AudioLoadResultHandler handler) {
		MusicEngine engine = mode();
		log.debug("Loading with engine {}: {}", engine, query);

		switch (engine) {
			case YOUTUBE_DIRECT -> requireDirect().load(query, handler);
			case YTDLP -> loadViaYtDlp(query, handler, true);
			case AUTO -> loadWithFallback(query, handler);
		}
	}

	/** Resolves a stream URL through yt-dlp, for the Music screen's "test engine" action. */
	public Optional<String> testYtDlp(String query) {
		return ytDlp.resolveStreamUrl(YtDlpTargets.normalize(query));
	}

	// ------------------------------------------------------------------ strategies

	private void loadWithFallback(String query, AudioLoadResultHandler handler) {
		DirectLoader direct = directLoader.getIfAvailable();
		if (direct == null || !direct.isAvailable()) {
			loadViaYtDlp(query, handler, true);
			return;
		}
		direct.load(query, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				handler.trackLoaded(track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				handler.playlistLoaded(playlist);
			}

			@Override
			public void noMatches() {
				log.info("Direct engine found nothing; falling back to yt-dlp");
				loadViaYtDlp(query, handler, true);
			}

			@Override
			public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
				log.info("Direct engine failed ({}); falling back to yt-dlp", exception.getMessage());
				loadViaYtDlp(query, handler, true);
			}
		});
	}

	/**
	 * yt-dlp path: try the direct stream URL first (no disk), then a cached download.
	 *
	 * @param allowDownload false when only a stream is acceptable
	 */
	private void loadViaYtDlp(String query, AudioLoadResultHandler handler, boolean allowDownload) {
		if (!ytDlp.isAvailable()) {
			handler.loadFailed(new com.sedmelluq.discord.lavaplayer.tools.FriendlyException(
				"The yt-dlp engine is not installed",
				com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON,
				new IllegalStateException("yt-dlp missing")));
			return;
		}
		DirectLoader direct = directLoader.getIfAvailable();
		if (direct == null || !direct.isAvailable()) {
			handler.loadFailed(new com.sedmelluq.discord.lavaplayer.tools.FriendlyException(
				"The in-process engine is not available",
				com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON,
				new IllegalStateException("direct loader missing")));
			return;
		}

		String target = YtDlpTargets.normalize(query);
		Optional<String> streamUrl = ytDlp.resolveStreamUrl(target);

		if (streamUrl.isPresent()) {
			direct.load(streamUrl.get(), new AudioLoadResultHandler() {
				@Override
				public void trackLoaded(AudioTrack track) {
					handler.trackLoaded(track);
				}

				@Override
				public void playlistLoaded(AudioPlaylist playlist) {
					handler.playlistLoaded(playlist);
				}

				@Override
				public void noMatches() {
					downloadAndLoad(target, query, handler, allowDownload);
				}

				@Override
				public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
					log.info("Resolved stream could not be played ({}); trying a cached download",
						exception.getMessage());
					downloadAndLoad(target, query, handler, allowDownload);
				}
			});
			return;
		}
		downloadAndLoad(target, query, handler, allowDownload);
	}

	private void downloadAndLoad(String target, String originalQuery, AudioLoadResultHandler handler,
			boolean allowDownload) {
		if (!allowDownload) {
			handler.noMatches();
			return;
		}
		String baseName = YtDlpTargets.videoId(originalQuery).orElseGet(() -> "track-" + Integer.toHexString(
			originalQuery.hashCode()));
		Optional<java.nio.file.Path> file = ytDlp.download(target, baseName);
		if (file.isEmpty()) {
			handler.noMatches();
			return;
		}
		DirectLoader direct = directLoader.getIfAvailable();
		if (direct == null) {
			handler.noMatches();
			return;
		}
		direct.loadFile(file.get(), handler);
	}

	private DirectLoader requireDirect() {
		DirectLoader loader = directLoader.getIfAvailable();
		if (loader == null || !loader.isAvailable()) {
			throw new ResolutionException("music.error.directUnavailable");
		}
		return loader;
	}
}
