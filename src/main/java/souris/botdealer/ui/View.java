/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

/**
 * Root views the router can display. Sections inside the main shell are
 * {@link Section}.
 */
public enum View {

	/** First-run setup wizard (also shown after a reset). */
	WIZARD("/fxml/wizard/wizard.fxml"),

	/** Main application shell with sidebar navigation. */
	SHELL("/fxml/main-shell.fxml");

	private final String fxmlPath;

	View(String fxmlPath) {
		this.fxmlPath = fxmlPath;
	}

	public String fxmlPath() {
		return fxmlPath;
	}
}
