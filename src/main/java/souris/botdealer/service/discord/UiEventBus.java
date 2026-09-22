/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one bridge from background work (JDA threads, schedulers) to the JavaFX UI.
 *
 * <p>Nothing outside the UI may touch JavaFX objects, so every event published here is
 * re-emitted on the FX application thread. When the toolkit is not running (unit tests,
 * {@code --self-test}) listeners are called inline instead of failing.</p>
 */
@Component
public class UiEventBus {

	private static final Logger log = LoggerFactory.getLogger(UiEventBus.class);

	private final List<Consumer<Object>> listeners = new CopyOnWriteArrayList<>();

	public void subscribe(Consumer<Object> listener) {
		listeners.add(listener);
	}

	public void publish(Object event) {
		runOnFxThread(() -> {
			for (Consumer<Object> listener : listeners) {
				try {
					listener.accept(event);
				} catch (RuntimeException e) {
					log.warn("UI listener failed for {}: {}", event.getClass().getSimpleName(), e.toString());
				}
			}
		});
	}

	private static void runOnFxThread(Runnable action) {
		try {
			if (Platform.isFxApplicationThread()) {
				action.run();
			} else {
				Platform.runLater(action);
			}
		} catch (IllegalStateException | UnsupportedOperationException e) {
			// Toolkit not started: self-test and unit tests still get the notification.
			action.run();
		}
	}
}
