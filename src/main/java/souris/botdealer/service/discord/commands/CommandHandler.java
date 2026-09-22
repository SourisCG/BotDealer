/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * A group of slash commands. Implementations only translate between Discord and the
 * domain services; all rules live in the services so they stay testable without a
 * Discord connection.
 *
 * <p>A handler may expose several root commands (music has one per action) or a single
 * root with subcommands (betting).</p>
 */
public interface CommandHandler {

	/** Definitions registered with Discord. */
	List<CommandData> definitions();

	/** Executes the interaction. Exceptions are translated by the dispatcher. */
	void handle(SlashCommandInteractionEvent event);

	/** Root command name of the first definition, for logging and diagnostics. */
	default String rootName() {
		return definitions().isEmpty() ? "" : definitions().get(0).getName();
	}
}
