/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

/**
 * Content sections inside the main shell. Each maps to an FXML loaded into the
 * shell's content area.
 */
public enum Section {

	DASHBOARD("dashboard", "/fxml/sections/dashboard.fxml"),
	EVENTS("events", "/fxml/sections/placeholder.fxml"),
	WALLETS("wallets", "/fxml/sections/placeholder.fxml"),
	MUSIC("music", "/fxml/sections/placeholder.fxml"),
	TTS("tts", "/fxml/sections/placeholder.fxml"),
	SETTINGS("settings", "/fxml/sections/settings.fxml"),
	ABOUT("about", "/fxml/sections/about.fxml");

	private final String id;
	private final String fxmlPath;

	Section(String id, String fxmlPath) {
		this.id = id;
		this.fxmlPath = fxmlPath;
	}

	public String id() {
		return id;
	}

	public String fxmlPath() {
		return fxmlPath;
	}

	/** Sidebar label key, e.g. {@code nav.dashboard}. */
	public String labelKey() {
		return "nav." + id;
	}
}
