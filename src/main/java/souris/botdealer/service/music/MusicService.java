/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import souris.botdealer.service.discord.DiscordBotService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.music.engine.EngineRouter;
import souris.botdealer.service.music.engine.ResolutionException;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

/**
 * Music for every guild: joins voice channels, resolves requests through the engine
 * router and keeps one {@link GuildMusicManager} per guild.
 *
 * <p>Resolution is asynchronous. LavaPlayer loads in its own threads and the AUTO
 * fallback may block on yt-dlp, so callers get their answer through
 * {@link MusicEvents} rather than a return value.</p>
 */
@Service
public class MusicService {

	private static final Logger log = LoggerFactory.getLogger(MusicService.class);

	private final DiscordBotService bot;
	private final EngineRouter router;
	private final AppSettingsService settings;
	private final GuildConfigService guildConfig;
	private final UiEventBus uiEvents;

	private final ObjectProvider<DirectLoaderImpl> directLoader;

	private final Map<Long, GuildMusicManager> managers = new ConcurrentHashMap<>();

	public MusicService(DiscordBotService bot, EngineRouter router, AppSettingsService settings,
			GuildConfigService guildConfig, UiEventBus uiEvents,
			ObjectProvider<DirectLoaderImpl> directLoader) {
		this.bot = bot;
		this.router = router;
		this.settings = settings;
		this.guildConfig = guildConfig;
		this.uiEvents = uiEvents;
		this.directLoader = directLoader;
	}

	// ------------------------------------------------------------------ requests

	/**
	 * Resolves a request and queues it, joining the caller's voice channel when needed.
	 *
	 * @param query a YouTube URL or a search phrase
	 */
	public void play(long guildId, long userId, String query) {
		Guild guild = requireGuild(guildId);
		if (guild == null) {
			return;
		}
		AudioChannel channel = voiceChannelOf(guild, userId);
		if (channel == null) {
			uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, "music.error.joinVoiceFirst", ""));
			return;
		}
		if (!join(guild, channel)) {
			return;
		}

		int maxMinutes = Math.max(0, settings.getInt(AppSettingKey.MUSIC_MAX_TRACK_MINUTES));
		GuildMusicManager manager = manager(guildId);

		try {
			router.load(query, new AudioLoadResultHandler() {
				@Override
				public void trackLoaded(AudioTrack track) {
					accept(manager, track, maxMinutes);
				}

				@Override
				public void playlistLoaded(AudioPlaylist playlist) {
					// A search returns a playlist of candidates; take the best one. A real
					// playlist is queued up to the remaining space.
					if (playlist.getSelectedTrack() != null || playlist.isSearchResult()) {
						accept(manager, playlist.getSelectedTrack() != null
							? playlist.getSelectedTrack()
							: playlist.getTracks().get(0), maxMinutes);
						return;
					}
					int queued = 0;
					for (AudioTrack track : playlist.getTracks()) {
						if (tooLong(track, maxMinutes)) {
							continue;
						}
						if (!manager.scheduler().enqueue(track)) {
							break;
						}
						queued++;
					}
					uiEvents.publish(new MusicEvents.TrackQueued(guildId,
						playlist.getName() + " (" + queued + ")", 0, manager.scheduler().size()));
				}

				@Override
				public void noMatches() {
					uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, "music.error.notFound", ""));
				}

				@Override
				public void loadFailed(FriendlyException exception) {
					uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, "music.error.loadFailed",
						exception.getMessage() == null ? "" : exception.getMessage()));
				}
			});
		} catch (ResolutionException e) {
			uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, e.messageKey(), ""));
		} catch (Exception e) {
			log.error("Unexpected failure while resolving a request: {}", e.toString());
			uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, "music.error.loadFailed", ""));
		}
	}

	private void accept(GuildMusicManager manager, AudioTrack track, int maxMinutes) {
		long guildId = manager.guildId();
		if (tooLong(track, maxMinutes)) {
			uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, "music.error.tooLong",
				Long.toString(maxMinutes)));
			return;
		}
		if (!manager.scheduler().enqueue(track)) {
			uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, "music.error.queueFull",
				Integer.toString(manager.scheduler().maxQueueSize())));
		}
	}

	private static boolean tooLong(AudioTrack track, int maxMinutes) {
		if (maxMinutes <= 0 || track.getInfo().isStream) {
			return false;
		}
		return track.getDuration() > maxMinutes * 60_000L;
	}

	// ------------------------------------------------------------------ voice

	/** Joins a channel, moving the connection when already elsewhere. */
	public boolean join(Guild guild, AudioChannel channel) {
		AudioManager audioManager = guild.getAudioManager();
		if (audioManager.isConnected() && audioManager.getConnectedChannel() != null
				&& audioManager.getConnectedChannel().getIdLong() == channel.getIdLong()) {
			return true;
		}
		audioManager.setSendingHandler(manager(guild.getIdLong()).sendHandler());
		audioManager.openAudioConnection(channel);
		log.info("Joining voice channel {} in guild {}", channel.getName(), guild.getIdLong());
		uiEvents.publish(new MusicEvents.VoiceConnectionChanged(guild.getIdLong(), true));
		return true;
	}

	/** Joins by id, for the Music screen. */
	public boolean join(long guildId, long channelId) {
		Guild guild = requireGuild(guildId);
		if (guild == null) {
			return false;
		}
		var channel = guild.getVoiceChannelById(channelId);
		if (channel == null) {
			uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, "music.error.channelNotFound", ""));
			return false;
		}
		return join(guild, channel);
	}

	public void leave(long guildId) {
		Guild guild = requireGuild(guildId);
		if (guild == null) {
			return;
		}
		GuildMusicManager manager = managers.remove(guildId);
		if (manager != null) {
			manager.shutdown();
		}
		guild.getAudioManager().closeAudioConnection();
		uiEvents.publish(new MusicEvents.VoiceConnectionChanged(guildId, false));
		uiEvents.publish(new MusicEvents.PlaybackStopped(guildId));
	}

	public boolean isConnected(long guildId) {
		Guild guild = requireGuild(guildId);
		return guild != null && guild.getAudioManager().isConnected();
	}

	// ------------------------------------------------------------------ controls

	public boolean skip(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		if (manager == null || manager.nowPlaying().isEmpty()) {
			return false;
		}
		manager.scheduler().next();
		return true;
	}

	public boolean pause(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		if (manager == null || !manager.isPlaying()) {
			return false;
		}
		manager.player().setPaused(true);
		return true;
	}

	public boolean resume(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		if (manager == null || !manager.isPaused()) {
			return false;
		}
		manager.player().setPaused(false);
		return true;
	}

	/** Stops playback and clears the queue but stays in the channel. */
	public boolean stop(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		if (manager == null) {
			return false;
		}
		manager.scheduler().clear(true);
		return true;
	}

	public void setVolume(long guildId, int volume) {
		manager(guildId).scheduler().setVolume(volume);
	}

	public RepeatMode cycleRepeat(long guildId) {
		TrackScheduler scheduler = manager(guildId).scheduler();
		scheduler.setRepeat(scheduler.repeat().next());
		return scheduler.repeat();
	}

	public boolean toggleShuffle(long guildId) {
		TrackScheduler scheduler = manager(guildId).scheduler();
		scheduler.setShuffle(!scheduler.shuffle());
		return scheduler.shuffle();
	}

	public boolean removeFromQueue(long guildId, int position) {
		GuildMusicManager manager = managers.get(guildId);
		return manager != null && manager.scheduler().removeAt(position);
	}

	// ------------------------------------------------------------------ reads

	public Optional<AudioTrack> nowPlaying(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		return manager == null ? Optional.empty() : manager.nowPlaying();
	}

	public List<AudioTrack> queue(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		return manager == null ? List.of() : manager.queue();
	}

	public boolean isPaused(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		return manager != null && manager.isPaused();
	}

	public int volume(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		return manager == null ? defaultVolume() : manager.scheduler().volume();
	}

	public RepeatMode repeat(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		return manager == null ? RepeatMode.OFF : manager.scheduler().repeat();
	}

	public boolean shuffle(long guildId) {
		GuildMusicManager manager = managers.get(guildId);
		return manager != null && manager.scheduler().shuffle();
	}

	/**
	 * Whether the member may control playback: Manage Server/Administrator, the
	 * configured DJ role, or simply being in the channel the bot is playing in.
	 */
	public boolean canControl(long guildId, long userId) {
		Guild guild = requireGuild(guildId);
		if (guild == null) {
			return false;
		}
		Member member = guild.getMemberById(userId);
		if (member != null && (member.hasPermission(Permission.MANAGE_SERVER)
				|| member.hasPermission(Permission.ADMINISTRATOR))) {
			return true;
		}
		Long djRole = guildConfig.djRoleId(guildId);
		if (djRole != null && member != null && member.getRoles().stream()
				.anyMatch(role -> role.getIdLong() == djRole)) {
			return true;
		}
		AudioChannel botChannel = guild.getAudioManager().getConnectedChannel();
		if (botChannel == null || member == null || member.getVoiceState() == null
				|| member.getVoiceState().getChannel() == null) {
			return false;
		}
		return member.getVoiceState().getChannel().getIdLong() == botChannel.getIdLong();
	}

	/** Leaves every channel and releases the players; called on shutdown. */
	public void shutdown() {
		managers.keySet().forEach(this::leave);
		managers.clear();
	}

	// ------------------------------------------------------------------ helpers

	/** One manager per guild, created on first use. */
	private GuildMusicManager manager(long guildId) {
		return managers.computeIfAbsent(guildId, id -> {
			int maxQueue = Math.max(1, settings.getInt(AppSettingKey.MUSIC_MAX_QUEUE_SIZE));
			var manager = new GuildMusicManager(id, directPlayerManager(), maxQueue, uiEvents::publish);
			manager.scheduler().setVolume(defaultVolume());
			log.debug("Created music manager for guild {}", id);
			return manager;
		});
	}

	/** The player manager comes from the in-process engine. */
	private AudioPlayerManager directPlayerManager() {
		DirectLoaderImpl loader = directLoader.getIfAvailable();
		if (loader == null) {
			throw new ResolutionException("music.error.notInstalled");
		}
		return loader.playerManager();
	}

	private int defaultVolume() {
		int volume = settings.getInt(AppSettingKey.MUSIC_DEFAULT_VOLUME);
		return Math.max(0, Math.min(200, volume));
	}

	private Guild requireGuild(long guildId) {
		Guild guild = bot.jda().map(jda -> jda.getGuildById(guildId)).orElse(null);
		if (guild == null) {
			uiEvents.publish(new MusicEvents.PlaybackFailed(guildId, "music.error.botOffline", ""));
		}
		return guild;
	}

	private static AudioChannel voiceChannelOf(Guild guild, long userId) {
		Member member = guild.getMemberById(userId);
		if (member == null || member.getVoiceState() == null) {
			return null;
		}
		return member.getVoiceState().getChannel();
	}
}
