/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import javafx.stage.Stage;
import org.springframework.context.ApplicationEvent;

/**
 * Published once the JavaFX toolkit is up and the primary {@link Stage} is available.
 * UI bootstrapping reacts to this event instead of living inside the Application class,
 * which keeps Spring wiring (and tests) straightforward.
 */
public class StageReadyEvent extends ApplicationEvent {

	private final Stage stage;

	public StageReadyEvent(Stage stage) {
		super(stage);
		this.stage = stage;
	}

	public Stage getStage() {
		return stage;
	}
}
