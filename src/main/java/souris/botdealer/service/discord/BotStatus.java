/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

/** Connection state of the Discord bot, as shown in the shell status bar. */
public enum BotStatus {

	STOPPED("bot.status.stopped"),
	STARTING("bot.status.starting"),
	CONNECTED("bot.status.connected"),
	ERROR("bot.status.error");

	private final String labelKey;

	BotStatus(String labelKey) {
		this.labelKey = labelKey;
	}

	public String labelKey() {
		return labelKey;
	}

	public boolean isConnected() {
		return this == CONNECTED;
	}
}
