/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.math.BigDecimal;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import souris.botdealer.domain.Bet;
import souris.botdealer.domain.BetEvent;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.betting.BetService;
import souris.botdealer.service.betting.BetValidationException;
import souris.botdealer.service.betting.EventQueryService;
import souris.botdealer.service.discord.DiscordMessages;
import souris.botdealer.service.discord.EventAnnouncer;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;
import souris.botdealer.service.economy.GuildConfigService;

/** {@code /bet place|list|mine}. */
@Component
public class BetCommands extends AbstractCommandHandler {

	public static final String ROOT = "bet";

	private final BetService bets;
	private final EventQueryService queries;
	private final EventAnnouncer announcer;
	private final UiEventBus uiEvents;

	public BetCommands(I18nService i18n, GuildConfigService guildConfig, BetService bets,
			EventQueryService queries, EventAnnouncer announcer, UiEventBus uiEvents) {
		super(i18n, guildConfig);
		this.bets = bets;
		this.queries = queries;
		this.announcer = announcer;
		this.uiEvents = uiEvents;
	}

	@Override
	public String rootName() {
		return ROOT;
	}

	@Override
	public java.util.List<CommandData> definitions() {
		return java.util.List.of(Commands.slash(ROOT, DiscordMessages.CMD_BET)
			.addSubcommands(
				new SubcommandData("place", DiscordMessages.CMD_BET_PLACE)
					.addOptions(
						new OptionData(OptionType.INTEGER, "event", DiscordMessages.OPT_EVENT, true),
						new OptionData(OptionType.INTEGER, "option", DiscordMessages.OPT_OPTION, true),
						new OptionData(OptionType.NUMBER, "amount", DiscordMessages.OPT_AMOUNT, true)
							.setMinValue(0.01)),
				new SubcommandData("list", DiscordMessages.CMD_BET_LIST)
					.addOptions(new OptionData(OptionType.INTEGER, "event", DiscordMessages.OPT_EVENT, false)),
				new SubcommandData("mine", DiscordMessages.CMD_BET_MINE)));
	}

	@Override
	public void handle(SlashCommandInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		switch (String.valueOf(event.getSubcommandName())) {
			case "place" -> place(event, guildId);
			case "list" -> list(event, guildId);
			default -> mine(event, guildId);
		}
	}

	private void place(SlashCommandInteractionEvent event, long guildId) {
		long eventId = event.getOption("event").getAsLong();
		long optionId = event.getOption("option").getAsLong();
		BigDecimal amount = BigDecimal.valueOf(event.getOption("amount").getAsDouble());

		BetService.PlacedBet placed = bets.place(guildId, event.getUser().getIdLong(),
			event.getUser().getEffectiveName(), eventId, optionId, amount);

		event.reply(text(event, DiscordMessages.REPLY_BET_PLACED, placed.optionLabel(),
			money(event, placed.amount()), "#" + eventId, money(event, placed.balanceAfter())))
			.setEphemeral(true).queue();

		announcer.refresh(eventId);
		uiEvents.publish(new UiEvents.EventChanged(eventId));
		uiEvents.publish(new UiEvents.EconomyChanged(guildId));
	}

	private void list(SlashCommandInteractionEvent event, long guildId) {
		OptionMapping eventOption = event.getOption("event");
		if (eventOption != null) {
			long eventId = eventOption.getAsLong();
			EventQueryService.EventView view = queries.view(eventId)
				.orElseThrow(() -> new BetValidationException("event.error.notFound"));
			event.replyEmbeds(EventEmbeds.build(i18n, locale(event), view, currency(event),
				guildConfig.currencySymbol(guildId))).setEphemeral(true).queue();
			return;
		}

		List<EventQueryService.EventView> open = queries.openEvents(guildId).stream()
			.map(queries::toView)
			.toList();
		if (open.isEmpty()) {
			event.reply(text(event, DiscordMessages.REPLY_EVENT_NONE)).setEphemeral(true).queue();
			return;
		}
		StringBuilder builder = new StringBuilder(text(event, DiscordMessages.REPLY_EVENT_LIST)).append('\n');
		open.forEach(view -> builder.append("• ")
			.append(EventEmbeds.describeEvent(i18n, locale(event), view, currency(event))).append('\n'));
		event.reply(builder.toString()).setEphemeral(true).queue();
	}

	private void mine(SlashCommandInteractionEvent event, long guildId) {
		List<Bet> mine = bets.betsOf(guildId, event.getUser().getIdLong());
		if (mine.isEmpty()) {
			event.reply(text(event, DiscordMessages.REPLY_BET_NONE)).setEphemeral(true).queue();
			return;
		}
		StringBuilder builder = new StringBuilder(text(event, DiscordMessages.REPLY_BET_LIST)).append('\n');
		for (Bet bet : mine.stream().limit(15).toList()) {
			BetEvent betEvent = queries.find(bet.getEventId()).orElse(null);
			if (betEvent == null) {
				continue;
			}
			String optionLabel = betEvent.getOptions().stream()
				.filter(option -> option.getId() != null && option.getId() == bet.getOptionId())
				.map(option -> option.getLabel())
				.findFirst().orElse("?");
			builder.append("• ").append(EventEmbeds.describeBet(i18n, locale(event), betEvent, bet,
				optionLabel, guildConfig.currencySymbol(guildId))).append('\n');
		}
		event.reply(builder.toString()).setEphemeral(true).queue();
	}
}
