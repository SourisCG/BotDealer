/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * What the UI and the now-playing embed need to know about playback, without either of
 * them touching LavaPlayer.
 */
public final class MusicEvents {

	private MusicEvents() {
	}

	/** A track began playing. */
	public record TrackStarted(long guildId, String title, String author, String uri, long durationMillis,
			String artworkUrl) {

		public static TrackStarted of(long guildId, AudioTrack track) {
			var info = track.getInfo();
			return new TrackStarted(guildId, info.title, info.author, info.uri, info.length, info.artworkUrl);
		}
	}

	/** The queue changed (added, removed, cleared, advanced). */
	public record QueueChanged(long guildId, int size) {
	}

	/** Playback stopped and the queue is empty. */
	public record PlaybackStopped(long guildId) {
	}

	/** A track was accepted into the queue. */
	public record TrackQueued(long guildId, String title, long durationMillis, int position) {
	}

	/** Something could not be played; {@code messageKey} is an i18n key. */
	public record PlaybackFailed(long guildId, String messageKey, String detail) {
	}

	/** The bot joined, moved or left a voice channel. */
	public record VoiceConnectionChanged(long guildId, boolean connected) {
	}

	/** Repeat or shuffle changed. */
	public record PlaybackModeChanged(long guildId, RepeatMode repeat, boolean shuffle, int volume) {
	}
}
