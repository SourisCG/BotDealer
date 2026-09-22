/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music;

import java.util.List;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail;
import dev.lavalink.youtube.clients.MusicWithThumbnail;
import dev.lavalink.youtube.clients.WebWithThumbnail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;
import souris.botdealer.service.music.engine.DirectLoader;

/**
 * The in-process engine: LavaPlayer with the live YouTube source manager.
 *
 * <p>Two details that matter:</p>
 * <ul>
 *   <li>LavaPlayer still ships its own <b>deprecated</b> YouTube source. It is excluded
 *       explicitly, otherwise it would be tried first and shadow the maintained one.</li>
 *   <li>{@code HttpAudioSourceManager} is registered because the yt-dlp fallback hands
 *       LavaPlayer a direct stream URL, and {@code LocalAudioSourceManager} because the
 *       same fallback can hand it a downloaded file.</li>
 * </ul>
 *
 * <p>If a YouTube OAuth refresh token is stored, it is applied here so age-restricted
 * content and bot checks are handled the way the user configured.</p>
 */
@Component
public class DirectLoaderImpl implements DirectLoader {

	private static final Logger log = LoggerFactory.getLogger(DirectLoaderImpl.class);

	private static final int FRAME_BUFFER_MILLIS = 1000;
	private static final int LOADER_THREADS = 4;
	private static final int PLAYLIST_PAGE_LIMIT = 10;

	private final DefaultAudioPlayerManager playerManager;
	private final dev.lavalink.youtube.YoutubeAudioSourceManager youtubeSource;
	private final boolean available;

	public DirectLoaderImpl(SecretService secrets) {
		this.playerManager = new DefaultAudioPlayerManager();
		playerManager.setFrameBufferDuration(FRAME_BUFFER_MILLIS);
		playerManager.setItemLoaderThreadPoolSize(LOADER_THREADS);

		// Http (resolved streams) and local files (downloads, TTS output).
		playerManager.registerSourceManager(new HttpAudioSourceManager());
		playerManager.registerSourceManager(new LocalAudioSourceManager());

		dev.lavalink.youtube.YoutubeAudioSourceManager created = null;
		try {
			created = new dev.lavalink.youtube.YoutubeAudioSourceManager(true,
				new MusicWithThumbnail(), new WebWithThumbnail(), new AndroidVrWithThumbnail());
			created.setPlaylistPageCount(PLAYLIST_PAGE_LIMIT);
			dev.lavalink.youtube.YoutubeAudioSourceManager source = created;
			secrets.get(SecretKey.YOUTUBE_OAUTH_REFRESH_TOKEN).ifPresent(token -> {
				source.useOauth2(token, true);
				log.info("YouTube account linking is active");
			});
			playerManager.registerSourceManager(created);
		} catch (Throwable t) {
			log.error("Could not register the YouTube source; music will not resolve: {}", t.toString());
			created = null;
		}
		this.youtubeSource = created;
		this.available = created != null;
	}

	@Override
	public void load(String identifier, AudioLoadResultHandler handler) {
		playerManager.loadItem(identifier, handler);
	}

	@Override
	public boolean isAvailable() {
		return available;
	}

	@Override
	public List<String> sourceNames() {
		return playerManager.getSourceManagers().stream()
			.map(manager -> manager.getSourceName())
			.toList();
	}

	/** The live YouTube source manager, used by the account-linking flow. */
	public java.util.Optional<dev.lavalink.youtube.YoutubeAudioSourceManager> youtubeSource() {
		return java.util.Optional.ofNullable(youtubeSource);
	}

	/** The manager the guild players are created from. */
	public AudioPlayerManager playerManager() {
		return playerManager;
	}

	/** Releases the loader threads when the application shuts down. */
	public void shutdown() {
		playerManager.shutdown();
	}
}
