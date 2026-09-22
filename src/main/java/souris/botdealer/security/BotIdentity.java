/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

/**
 * Identity returned by Discord for a valid bot token. Contains no secret material.
 */
public record BotIdentity(String id, String username, String globalName, String avatarUrl, boolean bot) {

	/** Best-effort display name: global name when set, otherwise the username. */
	public String displayName() {
		return (globalName == null || globalName.isBlank()) ? username : globalName;
	}
}
