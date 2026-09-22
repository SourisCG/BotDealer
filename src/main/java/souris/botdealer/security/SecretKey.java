/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.security;

/**
 * Names of the credentials BotDealer keeps in the OS keychain (or the AES-GCM
 * fallback file). The values are the storage account identifiers and must stay
 * stable: renaming one orphans the stored secret.
 */
public enum SecretKey {

	/** Discord bot token used to connect the gateway. */
	DISCORD_TOKEN("discord_token"),

	/** YouTube OAuth refresh token used by the youtube-source engine (Phase 4). */
	YOUTUBE_OAUTH_REFRESH_TOKEN("youtube_oauth_refresh_token");

	private final String storageId;

	SecretKey(String storageId) {
		this.storageId = storageId;
	}

	public String storageId() {
		return storageId;
	}
}
