/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The queue and its rules for one guild.
 *
 * <p>Extends {@code AudioEventAdapter} so it hears track start/end/exception/stuck. The
 * queue is mutated from Discord command threads and read from LavaPlayer's audio thread,
 * so every access is guarded by one lock.</p>
 *
 * <p>Events are emitted through a callback rather than straight to the UI, which keeps
 * this class free of Spring and JavaFX and therefore unit-testable with a mocked
 * player.</p>
 */
public class TrackScheduler extends AudioEventAdapter {

	private static final Logger log = LoggerFactory.getLogger(TrackScheduler.class);

	private final long guildId;
	private final AudioPlayer player;
	private final int maxQueueSize;
	private final Consumer<Object> eventSink;

	private final Deque<AudioTrack> queue = new ArrayDeque<>();
	private final Object lock = new Object();

	private RepeatMode repeat = RepeatMode.OFF;
	private boolean shuffle;

	public TrackScheduler(long guildId, AudioPlayer player, int maxQueueSize, Consumer<Object> eventSink) {
		this.guildId = guildId;
		this.player = player;
		this.maxQueueSize = Math.max(1, maxQueueSize);
		this.eventSink = eventSink;
	}

	// ------------------------------------------------------------------ queue

	/**
	 * Adds a track. Plays immediately when nothing is playing.
	 *
	 * @return false when the queue is full
	 */
	public boolean enqueue(AudioTrack track) {
		int position;
		synchronized (lock) {
			if (player.getPlayingTrack() != null || player.isPaused()) {
				if (queue.size() >= maxQueueSize) {
					return false;
				}
				queue.addLast(track);
				position = queue.size();
			} else {
				player.playTrack(track);
				position = 0;
			}
		}
		eventSink.accept(new MusicEvents.TrackQueued(guildId, track.getInfo().title, track.getInfo().length,
			position));
		eventSink.accept(new MusicEvents.QueueChanged(guildId, size()));
		return true;
	}

	/** Advances to the next track, honouring the repeat mode. */
	public void next() {
		AudioTrack next;
		synchronized (lock) {
			next = queue.pollFirst();
			if (next == null) {
				player.stopTrack();
				eventSink.accept(new MusicEvents.PlaybackStopped(guildId));
				eventSink.accept(new MusicEvents.QueueChanged(guildId, 0));
				return;
			}
		}
		player.playTrack(next);
		eventSink.accept(new MusicEvents.QueueChanged(guildId, size()));
	}

	/** Empties the queue; the current track keeps playing unless {@code stopCurrent}. */
	public void clear(boolean stopCurrent) {
		synchronized (lock) {
			queue.clear();
		}
		if (stopCurrent) {
			player.stopTrack();
		}
		eventSink.accept(new MusicEvents.QueueChanged(guildId, 0));
	}

	/** Removes one entry by position (1-based, as shown in Discord). */
	public boolean removeAt(int position) {
		synchronized (lock) {
			if (position < 1 || position > queue.size()) {
				return false;
			}
			List<AudioTrack> remaining = new ArrayList<>(queue);
			AudioTrack removed = remaining.remove(position - 1);
			queue.clear();
			queue.addAll(remaining);
			log.debug("Removed {} from the queue of guild {}", removed.getInfo().title, guildId);
		}
		eventSink.accept(new MusicEvents.QueueChanged(guildId, size()));
		return true;
	}

	public List<AudioTrack> snapshot() {
		synchronized (lock) {
			return List.copyOf(queue);
		}
	}

	public int size() {
		synchronized (lock) {
			return queue.size();
		}
	}

	public boolean isEmpty() {
		return size() == 0;
	}

	public Optional<AudioTrack> nowPlaying() {
		return Optional.ofNullable(player.getPlayingTrack());
	}

	public int maxQueueSize() {
		return maxQueueSize;
	}

	// ------------------------------------------------------------------ modes

	public RepeatMode repeat() {
		return repeat;
	}

	public void setRepeat(RepeatMode repeat) {
		this.repeat = repeat == null ? RepeatMode.OFF : repeat;
		emitMode();
	}

	public boolean shuffle() {
		return shuffle;
	}

	public void setShuffle(boolean shuffle) {
		this.shuffle = shuffle;
		emitMode();
	}

	public int volume() {
		return player.getVolume();
	}

	public void setVolume(int volume) {
		player.setVolume(Math.max(0, Math.min(200, volume)));
		emitMode();
	}

	private void emitMode() {
		eventSink.accept(new MusicEvents.PlaybackModeChanged(guildId, repeat, shuffle, player.getVolume()));
	}

	// ------------------------------------------------------------------ events

	@Override
	public void onTrackStart(AudioPlayer player, AudioTrack track) {
		log.debug("Now playing in guild {}: {}", guildId, track.getInfo().title);
		eventSink.accept(MusicEvents.TrackStarted.of(guildId, track));
	}

	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
		// Repeat only makes sense for a track that actually finished: replaying one that
		// failed to load would loop the failure forever.
		if (endReason == AudioTrackEndReason.FINISHED && repeat == RepeatMode.TRACK) {
			player.playTrack(track.makeClone());
			return;
		}
		if (!endReason.mayStartNext) {
			return;
		}
		if (endReason == AudioTrackEndReason.FINISHED && repeat == RepeatMode.QUEUE) {
			synchronized (lock) {
				// next() immediately removes one entry, so the queue never stays above
				// the cap. Dropping the repeat here instead would silently break the loop
				// whenever the queue happens to be full.
				queue.addLast(track.makeClone());
			}
		}
		next();
	}

	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
		log.warn("Playback failed in guild {}: {}", guildId, exception.getMessage());
		eventSink.accept(new MusicEvents.PlaybackFailed(guildId, "music.error.playbackFailed",
			exception.getMessage() == null ? "" : exception.getMessage()));
		next();
	}

	@Override
	public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
		log.warn("Track stuck for {} ms in guild {}; skipping", thresholdMs, guildId);
		eventSink.accept(new MusicEvents.PlaybackFailed(guildId, "music.error.trackStuck", ""));
		next();
	}
}
