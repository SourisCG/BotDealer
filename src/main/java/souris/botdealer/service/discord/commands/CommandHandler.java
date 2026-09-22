/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * One root slash command ("bet", "event", ...). Implementations only translate between
 * Discord and the domain services; all rules live in the services so they stay testable
 * without a Discord connection.
 */
public interface CommandHandler {

	/** Root command name, unique across handlers. */
	String rootName();

	/** Definition registered with Discord (subcommands, options, descriptions). */
	CommandData definition();

	/** Executes the interaction. Exceptions are translated by the dispatcher. */
	void handle(SlashCommandInteractionEvent event);
}
