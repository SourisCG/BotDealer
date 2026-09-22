/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music;

import java.util.List;
import java.util.function.Consumer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.audio.AudioSendHandler;

/**
 * Everything that plays audio for one guild: the player, its queue and the bridge to
 * Discord. One instance per guild, because JDA allows exactly one send handler per
 * audio connection and sharing one would make two guilds pull from the same queue.
 */
public class GuildMusicManager {

	private final long guildId;
	private final AudioPlayer player;
	private final TrackScheduler scheduler;
	private final AudioSendHandler sendHandler;

	public GuildMusicManager(long guildId, AudioPlayerManager playerManager, int maxQueueSize,
			Consumer<Object> eventSink) {
		this.guildId = guildId;
		this.player = playerManager.createPlayer();
		this.scheduler = new TrackScheduler(guildId, player, maxQueueSize, eventSink);
		this.sendHandler = new LavaplayerSendHandler(player);
		player.addListener(scheduler);
	}

	public long guildId() {
		return guildId;
	}

	public AudioPlayer player() {
		return player;
	}

	public TrackScheduler scheduler() {
		return scheduler;
	}

	public AudioSendHandler sendHandler() {
		return sendHandler;
	}

	public boolean isPlaying() {
		return player.getPlayingTrack() != null;
	}

	public boolean isPaused() {
		return player.isPaused();
	}

	public List<com.sedmelluq.discord.lavaplayer.track.AudioTrack> queue() {
		return scheduler.snapshot();
	}

	public java.util.Optional<com.sedmelluq.discord.lavaplayer.track.AudioTrack> nowPlaying() {
		return java.util.Optional.ofNullable(player.getPlayingTrack());
	}

	/** Stops playback and drops the queue; used when the bot leaves a channel. */
	public void shutdown() {
		scheduler.clear(true);
		player.destroy();
	}
}
