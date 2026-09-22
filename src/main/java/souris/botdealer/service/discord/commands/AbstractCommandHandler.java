/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.util.Locale;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.economy.GuildConfigService;

/**
 * Shared plumbing for command handlers: per-guild translations and the effective
 * currency labels.
 */
public abstract class AbstractCommandHandler implements CommandHandler {

	protected final I18nService i18n;
	protected final GuildConfigService guildConfig;

	protected AbstractCommandHandler(I18nService i18n, GuildConfigService guildConfig) {
		this.i18n = i18n;
		this.guildConfig = guildConfig;
	}

	/** Locale of the guild the command came from, falling back to the app language. */
	protected Locale locale(SlashCommandInteractionEvent event) {
		return event.getGuild() == null ? i18n.getLocale() : guildConfig.locale(event.getGuild().getIdLong());
	}

	/** Localized text for the guild that issued the command. */
	protected String text(SlashCommandInteractionEvent event, String key, Object... args) {
		return i18n.get(locale(event), key, args);
	}

	/** The guild's currency label, e.g. {@code chorizos}. */
	protected String currency(SlashCommandInteractionEvent event) {
		return guildConfig.currencyPlural(event.getGuild().getIdLong());
	}

	/** Symbol + amount, e.g. {@code 🌭 120.50}. */
	protected String money(SlashCommandInteractionEvent event, java.math.BigDecimal amount) {
		return guildConfig.currencySymbol(event.getGuild().getIdLong()) + " "
			+ amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
	}
}
