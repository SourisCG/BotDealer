/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Background scheduler for periodic bot chores (closing expired events, refreshing
 * cards). Daemon threads, so a stuck task can never keep the JVM alive after the
 * window closes.
 */
@Configuration
public class SchedulerConfig {

	@Bean(destroyMethod = "shutdownNow")
	public ScheduledExecutorService botScheduler() {
		return Executors.newScheduledThreadPool(2, runnable -> {
			Thread thread = new Thread(runnable, "botdealer-scheduler");
			thread.setDaemon(true);
			return thread;
		});
	}
}
