/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import souris.botdealer.service.discord.DiscordMessages;
import souris.botdealer.ui.shell.MainShellController;

/** {@code /botdealer help|ping|version}: the "is it alive" commands. */
@Component
public class GeneralCommands extends AbstractCommandHandler {

	public static final String ROOT = "botdealer";

	public GeneralCommands(souris.botdealer.i18n.I18nService i18n,
			souris.botdealer.service.economy.GuildConfigService guildConfig) {
		super(i18n, guildConfig);
	}

	@Override
	public String rootName() {
		return ROOT;
	}

	@Override
	public java.util.List<CommandData> definitions() {
		return java.util.List.of(Commands.slash(ROOT, DiscordMessages.CMD_BOTDEALER)
			.addSubcommands(
				new SubcommandData("help", DiscordMessages.CMD_BOTDEALER_HELP),
				new SubcommandData("ping", DiscordMessages.CMD_BOTDEALER_PING),
				new SubcommandData("version", DiscordMessages.CMD_BOTDEALER_VERSION)));
	}

	@Override
	public void handle(SlashCommandInteractionEvent event) {
		String subcommand = event.getSubcommandName();
		if ("ping".equals(subcommand)) {
			long latency = event.getJDA().getGatewayPing();
			event.reply(text(event, DiscordMessages.REPLY_PING, latency)).setEphemeral(true).queue();
			return;
		}
		if ("version".equals(subcommand)) {
			event.reply(text(event, DiscordMessages.REPLY_VERSION, MainShellController.version()))
				.setEphemeral(true).queue();
			return;
		}
		event.replyEmbeds(HelpEmbed.build(i18n, locale(event))).setEphemeral(true).queue();
	}
}
