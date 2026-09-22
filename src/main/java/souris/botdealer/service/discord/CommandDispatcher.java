/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import java.util.Locale;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.betting.BetValidationException;
import souris.botdealer.service.discord.commands.CommandHandler;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.economy.InsufficientFundsException;

/**
 * Routes interactions to the right handler and turns failures into localized replies.
 *
 * <p>Commands are registered <b>per guild</b> on {@code GuildReady}: guild commands
 * appear immediately, while global commands can take up to an hour to propagate, which
 * would make a freshly installed bot look broken.</p>
 */
@Component
public class CommandDispatcher extends ListenerAdapter {

	private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

	private final CommandRegistry registry;
	private final I18nService i18n;
	private final GuildConfigService guildConfig;

	public CommandDispatcher(CommandRegistry registry, I18nService i18n, GuildConfigService guildConfig) {
		this.registry = registry;
		this.i18n = i18n;
		this.guildConfig = guildConfig;
	}

	@Override
	public void onReady(ReadyEvent event) {
		log.info("Bot ready: {} guild(s), commands {}", event.getGuildTotalCount(),
			String.join(", ", registry.names()));
	}

	@Override
	public void onGuildReady(GuildReadyEvent event) {
		long guildId = event.getGuild().getIdLong();
		event.getGuild().updateCommands()
			.addCommands(registry.commandData())
			.queue(
				commands -> log.debug("Registered {} commands in guild {}", commands.size(), guildId),
				failure -> log.warn("Could not register commands in guild {}: {}", guildId,
					failure.getMessage()));
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		CommandHandler handler = registry.byName().get(event.getName());
		if (handler == null) {
			event.reply(i18n.get(localeOf(event), DiscordMessages.ERROR_GENERIC)).setEphemeral(true).queue();
			return;
		}
		try {
			handler.handle(event);
		} catch (BetValidationException e) {
			replyError(event, i18n.get(localeOf(event), e.messageKey(), e.arguments()));
		} catch (InsufficientFundsException e) {
			replyError(event, i18n.get(localeOf(event), "bet.error.insufficientFunds", e.balance(),
				e.requested()));
		} catch (IllegalArgumentException e) {
			replyError(event, i18n.get(localeOf(event), DiscordMessages.ERROR_INVALID_AMOUNT));
		} catch (Exception e) {
			// Never surface a raw stack trace to users, and never log the token.
			log.error("Command /{} failed: {}", event.getName(), e.toString());
			replyError(event, i18n.get(localeOf(event), DiscordMessages.ERROR_GENERIC));
		}
	}

	private void replyError(SlashCommandInteractionEvent event, String message) {
		if (event.isAcknowledged()) {
			event.getHook().sendMessage(message).setEphemeral(true).queue();
		} else {
			event.reply(message).setEphemeral(true).queue();
		}
	}

	private Locale localeOf(SlashCommandInteractionEvent event) {
		return event.getGuild() == null ? i18n.getLocale() : guildConfig.locale(event.getGuild().getIdLong());
	}
}
