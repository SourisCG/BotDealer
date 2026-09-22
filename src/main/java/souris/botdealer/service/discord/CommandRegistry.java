/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.springframework.stereotype.Component;
import souris.botdealer.service.discord.commands.CommandHandler;

/**
 * Aggregates every {@link CommandHandler} into the list registered with Discord, and
 * resolves a handler by root command name.
 */
@Component
public class CommandRegistry {

	private final Map<String, CommandHandler> handlers = new LinkedHashMap<>();

	public CommandRegistry(List<CommandHandler> discovered) {
		discovered.stream()
			.sorted(java.util.Comparator.comparing(CommandHandler::rootName))
			.forEach(handler -> handler.definitions()
				.forEach(definition -> handlers.put(definition.getName(), handler)));
	}

	public List<CommandData> commandData() {
		return handlers.values().stream()
			.distinct()
			.flatMap(handler -> handler.definitions().stream())
			.toList();
	}

	public Map<String, CommandHandler> byName() {
		return Map.copyOf(handlers);
	}

	public List<String> names() {
		return List.copyOf(handlers.keySet());
	}
}
