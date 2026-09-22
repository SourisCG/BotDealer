/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import souris.botdealer.domain.BetEvent;
import souris.botdealer.domain.PayoutMode;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.betting.BetValidationException;
import souris.botdealer.service.betting.EventService;
import souris.botdealer.service.discord.DiscordMessages;
import souris.botdealer.service.discord.EventAnnouncer;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;
import souris.botdealer.service.economy.GuildConfigService;

/**
 * {@code /event create|close|settle|cancel} for guild managers.
 *
 * <p>Creating an event by command covers the common case (a title and two or three
 * options); the desktop app is the full editor, with per-option odds and a preview of
 * the payout rules.</p>
 */
@Component
public class EventAdminCommands extends AbstractCommandHandler {

	public static final String ROOT = "event";

	private static final int MAX_OPTIONS = 5;

	private final EventService events;
	private final EventAnnouncer announcer;
	private final BotPermissions permissions;
	private final UiEventBus uiEvents;

	public EventAdminCommands(I18nService i18n, GuildConfigService guildConfig, EventService events,
			EventAnnouncer announcer, BotPermissions permissions, UiEventBus uiEvents) {
		super(i18n, guildConfig);
		this.events = events;
		this.announcer = announcer;
		this.permissions = permissions;
		this.uiEvents = uiEvents;
	}

	@Override
	public String rootName() {
		return ROOT;
	}

	@Override
	public java.util.List<CommandData> definitions() {
		SubcommandData create = new SubcommandData("create", DiscordMessages.CMD_EVENT_CREATE)
			.addOptions(
				new OptionData(OptionType.STRING, "title", DiscordMessages.OPT_TITLE, true),
				new OptionData(OptionType.STRING, "option1", DiscordMessages.OPT_OPTION1, true),
				new OptionData(OptionType.STRING, "option2", DiscordMessages.OPT_OPTION2, true),
				new OptionData(OptionType.STRING, "option3", DiscordMessages.OPT_OPTION3, false),
				new OptionData(OptionType.STRING, "option4", DiscordMessages.OPT_OPTION4, false),
				new OptionData(OptionType.STRING, "option5", DiscordMessages.OPT_OPTION5, false),
				new OptionData(OptionType.STRING, "mode", DiscordMessages.OPT_MODE, false)
					.addChoice("parimutuel", "PARIMUTUEL")
					.addChoice("fixed_odds", "FIXED_ODDS"),
				new OptionData(OptionType.STRING, "odds", DiscordMessages.OPT_ODDS, false),
				new OptionData(OptionType.INTEGER, "closes_in_minutes", DiscordMessages.OPT_CLOSES_IN, false)
					.setMinValue(1));
		return java.util.List.of(Commands.slash(ROOT, DiscordMessages.CMD_EVENT)
			.addSubcommands(create,
				new SubcommandData("close", DiscordMessages.CMD_EVENT_CLOSE)
					.addOptions(new OptionData(OptionType.INTEGER, "event", DiscordMessages.OPT_EVENT, true)),
				new SubcommandData("settle", DiscordMessages.CMD_EVENT_SETTLE)
					.addOptions(
						new OptionData(OptionType.INTEGER, "event", DiscordMessages.OPT_EVENT, true),
						new OptionData(OptionType.INTEGER, "winner", DiscordMessages.OPT_WINNER, true)),
				new SubcommandData("cancel", DiscordMessages.CMD_EVENT_CANCEL)
					.addOptions(new OptionData(OptionType.INTEGER, "event", DiscordMessages.OPT_EVENT, true))));
	}

	@Override
	public void handle(SlashCommandInteractionEvent event) {
		if (!permissions.canManage(event)) {
			event.reply(text(event, DiscordMessages.ERROR_NOT_ADMIN)).setEphemeral(true).queue();
			return;
		}
		switch (String.valueOf(event.getSubcommandName())) {
			case "create" -> create(event);
			case "close" -> close(event);
			case "settle" -> settle(event);
			default -> cancel(event);
		}
	}

	private void create(SlashCommandInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		PayoutMode mode = PayoutMode.PARIMUTUEL;
		OptionMapping modeOption = event.getOption("mode");
		if (modeOption != null) {
			mode = PayoutMode.valueOf(modeOption.getAsString());
		}

		List<String> labels = new ArrayList<>();
		List<BigDecimal> odds = parseOdds(event.getOption("odds"), mode);
		for (int index = 1; index <= MAX_OPTIONS; index++) {
			OptionMapping option = event.getOption("option" + index);
			if (option != null && !option.getAsString().isBlank()) {
				labels.add(option.getAsString());
			}
		}

		List<EventService.OptionDraft> drafts = new ArrayList<>();
		for (int index = 0; index < labels.size(); index++) {
			BigDecimal fixedOdds = mode == PayoutMode.FIXED_ODDS && index < odds.size()
				? odds.get(index)
				: null;
			drafts.add(new EventService.OptionDraft(labels.get(index), fixedOdds));
		}

		OptionMapping closesIn = event.getOption("closes_in_minutes");
		Instant closesAt = closesIn == null ? null : Instant.now().plus(Duration.ofMinutes(closesIn.getAsLong()));

		BetEvent created = events.create(new EventService.CreateRequest(guildId, event.getUser().getIdLong(),
			event.getOption("title").getAsString(), "", mode, closesAt, null,
			event.getChannel().getIdLong(), drafts, true));

		event.reply(text(event, DiscordMessages.REPLY_EVENT_CREATED, "#" + created.getId(),
			created.getTitle())).setEphemeral(true).queue();
		announcer.refresh(created.getId());
		uiEvents.publish(new UiEvents.EventChanged(created.getId()));
	}

	private void close(SlashCommandInteractionEvent event) {
		long eventId = event.getOption("event").getAsLong();
		events.close(eventId);
		event.reply(text(event, DiscordMessages.REPLY_EVENT_CLOSED, "#" + eventId)).setEphemeral(true).queue();
		announcer.refresh(eventId);
		uiEvents.publish(new UiEvents.EventChanged(eventId));
	}

	private void settle(SlashCommandInteractionEvent event) {
		long eventId = event.getOption("event").getAsLong();
		long winner = event.getOption("winner").getAsLong();
		EventService.SettlementReport report = events.settle(eventId, winner);

		event.reply(text(event, DiscordMessages.REPLY_EVENT_SETTLED, "#" + eventId, report.winnerCount(),
			money(event, report.totalPaid()))).setEphemeral(true).queue();
		announcer.announce(eventId, text(event, report.outcomeKey()));
		announcer.refresh(eventId);
		uiEvents.publish(new UiEvents.EventChanged(eventId));
		uiEvents.publish(new UiEvents.EconomyChanged(event.getGuild().getIdLong()));
	}

	private void cancel(SlashCommandInteractionEvent event) {
		long eventId = event.getOption("event").getAsLong();
		EventService.SettlementReport report = events.cancel(eventId);

		event.reply(text(event, DiscordMessages.REPLY_EVENT_CANCELLED, "#" + eventId,
			money(event, report.totalPaid()))).setEphemeral(true).queue();
		announcer.announce(eventId, text(event, report.outcomeKey()));
		announcer.refresh(eventId);
		uiEvents.publish(new UiEvents.EventChanged(eventId));
		uiEvents.publish(new UiEvents.EconomyChanged(event.getGuild().getIdLong()));
	}

	/** Parses a comma-separated odds list such as {@code 2.0,3.5,1.8}. */
	private List<BigDecimal> parseOdds(OptionMapping option, PayoutMode mode) {
		if (option == null || option.getAsString().isBlank()) {
			if (mode == PayoutMode.FIXED_ODDS) {
				throw new BetValidationException("event.error.oddsTooLow", "?");
			}
			return List.of();
		}
		List<BigDecimal> odds = new ArrayList<>();
		for (String part : option.getAsString().split(",")) {
			String trimmed = part.strip();
			if (trimmed.isEmpty()) {
				continue;
			}
			try {
				odds.add(new BigDecimal(trimmed));
			} catch (NumberFormatException e) {
				throw new BetValidationException("discord.error.invalidAmount");
			}
		}
		return odds;
	}
}
