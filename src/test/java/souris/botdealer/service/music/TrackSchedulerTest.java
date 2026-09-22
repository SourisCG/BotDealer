/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Queue behaviour, with a mocked player so no audio is involved.
 *
 * <p>The cases that matter: a full queue must be refused, a stopped track must still
 * advance, a failed track must NOT be repeated forever, and the queue repeat has to put
 * the finished track back at the end.</p>
 */
class TrackSchedulerTest {

	private static final long GUILD = 42L;

	private AudioPlayer player;
	private TrackScheduler scheduler;
	private final List<Object> events = new ArrayList<>();

	@BeforeEach
	void setUp() {
		player = mock(AudioPlayer.class);
		when(player.getVolume()).thenReturn(100);
		scheduler = new TrackScheduler(GUILD, player, 3, events::add);
	}

	/** Stubs the player as playing a fresh track, avoiding nested Mockito stubbing. */
	private AudioTrack playing(String title) {
		AudioTrack track = track(title);
		when(player.getPlayingTrack()).thenReturn(track);
		return track;
	}

	private static AudioTrack track(String title) {
		AudioTrack track = mock(AudioTrack.class);
		when(track.getInfo()).thenReturn(new AudioTrackInfo(title, "author", 60_000L, "id-" + title, false,
			"https://youtu.be/" + title, null, null));
		when(track.makeClone()).thenReturn(track);
		return track;
	}

	@Test
	void firstTrackPlaysImmediately() {
		AudioTrack first = track("first");

		assertTrue(scheduler.enqueue(first));

		verify(player).playTrack(first);
		assertEquals(0, scheduler.size());
	}

	@Test
	void laterTracksWaitInTheQueue() {
		playing("playing");
		AudioTrack queued = track("queued");

		assertTrue(scheduler.enqueue(queued));

		verify(player, never()).playTrack(queued);
		assertEquals(1, scheduler.size());
	}

	@Test
	void refusesToOverflowTheQueue() {
		playing("playing");

		assertTrue(scheduler.enqueue(track("a")));
		assertTrue(scheduler.enqueue(track("b")));
		assertTrue(scheduler.enqueue(track("c")));
		assertFalse(scheduler.enqueue(track("d")), "the queue cap must be enforced");
		assertEquals(3, scheduler.size());
	}

	@Test
	void nextPlaysTheFollowingTrack() {
		playing("playing");
		AudioTrack queued = track("queued");
		scheduler.enqueue(queued);

		scheduler.next();

		verify(player).playTrack(queued);
		assertEquals(0, scheduler.size());
	}

	@Test
	void nextStopsPlaybackWhenTheQueueIsEmpty() {
		scheduler.next();

		verify(player).stopTrack();
		assertTrue(events.stream().anyMatch(event -> event instanceof MusicEvents.PlaybackStopped));
	}

	@Test
	void finishedTrackAdvances() {
		AudioTrack finished = track("finished");
		playing("next");

		scheduler.onTrackEnd(player, finished, AudioTrackEndReason.FINISHED);

		assertEquals(0, scheduler.size());
	}

	@Test
	void aStoppedTrackDoesNotAdvanceByItself() {
		// LavaPlayer reports STOPPED with mayStartNext = false, so /skip must not rely on
		// this event: MusicService.skip() calls next() explicitly.
		AudioTrack queued = track("queued");
		playing("playing");
		scheduler.enqueue(queued);

		scheduler.onTrackEnd(player, track("playing"), AudioTrackEndReason.STOPPED);

		verify(player, never()).playTrack(queued);
		assertEquals(1, scheduler.size());
	}

	@Test
	void skippingAdvancesEvenThoughStopTrackDoesNot() {
		AudioTrack queued = track("queued");
		playing("playing");
		scheduler.enqueue(queued);

		scheduler.next();

		verify(player).playTrack(queued);
	}

	@Test
	void cleanupDoesNotAdvance() {
		AudioTrack queued = track("queued");
		playing("playing");
		scheduler.enqueue(queued);

		scheduler.onTrackEnd(player, track("playing"), AudioTrackEndReason.CLEANUP);

		verify(player, never()).playTrack(queued);
		assertEquals(1, scheduler.size());
	}

	@Test
	void repeatTrackReplaysAFinishedTrack() {
		AudioTrack finished = track("finished");
		scheduler.setRepeat(RepeatMode.TRACK);

		scheduler.onTrackEnd(player, finished, AudioTrackEndReason.FINISHED);

		verify(finished).makeClone();
		verify(player).playTrack(finished);
	}

	@Test
	void repeatTrackDoesNotReplayAFailedTrack() {
		// Repeating a failure would loop forever, so only FINISHED repeats.
		AudioTrack failed = track("failed");
		scheduler.setRepeat(RepeatMode.TRACK);

		scheduler.onTrackEnd(player, failed, AudioTrackEndReason.LOAD_FAILED);

		verify(failed, never()).makeClone();
	}

	@Test
	void repeatQueuePutsTheFinishedTrackBackAtTheEnd() {
		AudioTrack finished = track("finished");
		AudioTrack next = track("next");
		playing("finished");
		scheduler.enqueue(next);
		scheduler.setRepeat(RepeatMode.QUEUE);

		scheduler.onTrackEnd(player, finished, AudioTrackEndReason.FINISHED);

		// 'next' starts playing and the finished track is waiting behind it.
		verify(player).playTrack(next);
		assertEquals(1, scheduler.size());
		assertEquals("finished", scheduler.snapshot().get(0).getInfo().title);
	}

	@Test
	void repeatQueueStaysWithinTheCapAfterAdvancing() {
		playing("playing");
		AudioTrack a = track("a");
		scheduler.enqueue(a);
		scheduler.enqueue(track("b"));
		scheduler.enqueue(track("c"));
		scheduler.setRepeat(RepeatMode.QUEUE);

		scheduler.onTrackEnd(player, track("finished"), AudioTrackEndReason.FINISHED);

		// 'a' starts playing and 'finished' waits at the end; the queue is back at the cap.
		verify(player).playTrack(a);
		assertEquals(3, scheduler.size());
		assertTrue(scheduler.size() <= scheduler.maxQueueSize());
		assertEquals("finished", scheduler.snapshot().get(2).getInfo().title);
	}

	@Test
	void repeatModeCycles() {
		assertEquals(RepeatMode.OFF, scheduler.repeat());
		assertEquals(RepeatMode.TRACK, scheduler.repeat().next());
		assertEquals(RepeatMode.QUEUE, scheduler.repeat().next().next());
		assertEquals(RepeatMode.OFF, scheduler.repeat().next().next().next());
	}

	@Test
	void removeAtUsesOneBasedPositions() {
		playing("playing");
		scheduler.enqueue(track("a"));
		scheduler.enqueue(track("b"));

		assertFalse(scheduler.removeAt(0), "position 0 does not exist");
		assertFalse(scheduler.removeAt(3), "position beyond the queue does not exist");
		assertTrue(scheduler.removeAt(1));
		assertEquals(1, scheduler.size());
		assertEquals("b", scheduler.snapshot().get(0).getInfo().title);
	}

	@Test
	void clearEmptiesTheQueue() {
		playing("playing");
		scheduler.enqueue(track("a"));
		scheduler.enqueue(track("b"));

		scheduler.clear(true);

		assertTrue(scheduler.isEmpty());
		verify(player).stopTrack();
	}

	@Test
	void volumeIsClampedToASaneRange() {
		scheduler.setVolume(500);
		verify(player).setVolume(200);

		scheduler.setVolume(-10);
		verify(player).setVolume(0);
	}

	@Test
	void failedAndStuckTracksAreReportedAndSkipped() {
		AudioTrack queued = track("queued");
		playing("playing");
		scheduler.enqueue(queued);

		scheduler.onTrackException(player, track("bad"),
			new com.sedmelluq.discord.lavaplayer.tools.FriendlyException("boom",
				com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON,
				new IllegalStateException("boom")));

		verify(player).playTrack(queued);
		assertTrue(events.stream().anyMatch(event -> event instanceof MusicEvents.PlaybackFailed));

		scheduler.onTrackStuck(player, track("stuck"), 10_000L);
		assertEquals(2, events.stream().filter(event -> event instanceof MusicEvents.PlaybackFailed).count());
	}

	@Test
	void trackStartIsAnnounced() {
		AudioTrack playing = track("song");
		when(playing.getInfo()).thenReturn(new AudioTrackInfo("song", "artist", 1234L, "id", false,
			"https://youtu.be/id", "https://img/thumb.png", null));

		scheduler.onTrackStart(player, playing);

		Optional<MusicEvents.TrackStarted> started = events.stream()
			.filter(MusicEvents.TrackStarted.class::isInstance)
			.map(MusicEvents.TrackStarted.class::cast)
			.findFirst();
		assertTrue(started.isPresent());
		assertEquals("song", started.get().title());
		assertEquals("artist", started.get().author());
		assertEquals("https://img/thumb.png", started.get().artworkUrl());
	}

	@Test
	void queueChangesAreAnnounced() {
		playing("playing");
		scheduler.enqueue(track("a"));

		assertTrue(events.stream().anyMatch(event -> event instanceof MusicEvents.QueueChanged));
	}

	@Test
	void nowPlayingReflectsThePlayer() {
		assertTrue(scheduler.nowPlaying().isEmpty());

		AudioTrack playing = track("playing");
		when(player.getPlayingTrack()).thenReturn(playing);

		assertEquals(playing, scheduler.nowPlaying().orElseThrow());
	}

	@Test
	void constructorRejectsANonsenseQueueSize() {
		TrackScheduler tiny = new TrackScheduler(GUILD, player, 0, events::add);
		playing("playing");

		assertTrue(tiny.enqueue(track("a")));
		assertFalse(tiny.enqueue(track("b")));
	}

	@Test
	void enqueueAcceptsAnyTrackType() {
		AudioTrack track = track("x");
		when(player.getPlayingTrack()).thenReturn(null);
		when(player.isPaused()).thenReturn(false);

		assertTrue(scheduler.enqueue(track));
		verify(player).playTrack(any(AudioTrack.class));
	}
}
