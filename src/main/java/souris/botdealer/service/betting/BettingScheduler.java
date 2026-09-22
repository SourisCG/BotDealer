/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.betting;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import souris.botdealer.service.discord.EventAnnouncer;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;

/**
 * Closes events whose betting window elapsed, so a closed event cannot keep taking bets
 * just because nobody pressed a button.
 *
 * <p>Runs on the shared daemon scheduler. Failures are logged and swallowed: a broken
 * announcement must not stop the next sweep.</p>
 */
@Component
public class BettingScheduler {

	private static final Logger log = LoggerFactory.getLogger(BettingScheduler.class);

	private static final long SWEEP_SECONDS = 30;

	private final EventService events;
	private final EventAnnouncer announcer;
	private final UiEventBus uiEvents;
	private final ScheduledExecutorService scheduler;

	private java.util.concurrent.ScheduledFuture<?> task;

	public BettingScheduler(EventService events, EventAnnouncer announcer, UiEventBus uiEvents,
			ScheduledExecutorService scheduler) {
		this.events = events;
		this.announcer = announcer;
		this.uiEvents = uiEvents;
		this.scheduler = scheduler;
	}

	@PostConstruct
	void schedule() {
		task = scheduler.scheduleWithFixedDelay(this::sweep, SWEEP_SECONDS, SWEEP_SECONDS, TimeUnit.SECONDS);
		log.debug("Betting scheduler started (every {}s)", SWEEP_SECONDS);
	}

	@PreDestroy
	void cancel() {
		if (task != null) {
			task.cancel(false);
		}
	}

	/** One pass; package-private so tests can run it deterministically. */
	void sweep() {
		try {
			List<Long> closed = events.closeExpired();
			for (Long eventId : closed) {
				announcer.refresh(eventId);
				uiEvents.publish(new UiEvents.EventChanged(eventId));
				log.info("Event {} closed automatically", eventId);
			}
		} catch (Exception e) {
			log.warn("Automatic event closing failed: {}", e.toString());
		}
	}
}
