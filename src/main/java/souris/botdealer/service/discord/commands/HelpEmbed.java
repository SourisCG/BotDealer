/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.util.Locale;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.discord.DiscordMessages;

/**
 * The help card. Built from the same translation keys the commands use, so it cannot
 * drift from the real command names.
 */
final class HelpEmbed {

	private static final int BRAND_TEAL = 0x38A3A5;

	private HelpEmbed() {
	}

	static MessageEmbed build(I18nService i18n, Locale locale) {
		EmbedBuilder embed = new EmbedBuilder()
			.setColor(BRAND_TEAL)
			.setTitle(i18n.get(locale, DiscordMessages.REPLY_HELP_TITLE))
			.setDescription(i18n.get(locale, DiscordMessages.REPLY_HELP));

		embed.addField(slash("botdealer"), bullet(i18n, locale,
			DiscordMessages.CMD_BOTDEALER_HELP, DiscordMessages.CMD_BOTDEALER_PING,
			DiscordMessages.CMD_BOTDEALER_VERSION), false);
		embed.addField(slash("chorizos"), bullet(i18n, locale,
			DiscordMessages.CMD_CHORIZOS_BALANCE, DiscordMessages.CMD_CHORIZOS_DAILY), false);
		embed.addField(slash("bet"), bullet(i18n, locale,
			DiscordMessages.CMD_BET_PLACE, DiscordMessages.CMD_BET_LIST, DiscordMessages.CMD_BET_MINE), false);
		embed.addField(slash("event") + " · " + slash("economy"), bullet(i18n, locale,
			DiscordMessages.CMD_EVENT_CREATE, DiscordMessages.CMD_EVENT_CLOSE,
			DiscordMessages.CMD_EVENT_SETTLE, DiscordMessages.CMD_EVENT_CANCEL,
			DiscordMessages.CMD_ECONOMY_GIVE, DiscordMessages.CMD_ECONOMY_REMOVE,
			DiscordMessages.CMD_ECONOMY_SET), false);
		return embed.build();
	}

	private static String slash(String name) {
		return "/" + name;
	}

	private static String bullet(I18nService i18n, Locale locale, String... keys) {
		StringBuilder builder = new StringBuilder();
		for (String key : keys) {
			if (!builder.isEmpty()) {
				builder.append('\n');
			}
			builder.append("• ").append(i18n.get(locale, key));
		}
		return builder.toString();
	}
}
