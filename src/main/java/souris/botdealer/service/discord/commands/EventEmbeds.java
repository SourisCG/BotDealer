/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import souris.botdealer.domain.BetEvent;
import souris.botdealer.domain.BetStatus;
import souris.botdealer.domain.EventStatus;
import souris.botdealer.domain.PayoutMode;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.betting.EventQueryService;
import souris.botdealer.service.discord.DiscordMessages;
import souris.botdealer.util.Money;

/**
 * The live event card posted in the channel and refreshed as bets arrive.
 *
 * <p>Colors follow the brand palette and encode the state at a glance: teal while open,
 * green when settled, red when cancelled.</p>
 */
public final class EventEmbeds {

	private static final int COLOR_OPEN = 0x38A3A5;
	private static final int COLOR_SETTLED = 0x80ED99;
	private static final int COLOR_CANCELLED = 0xC0392B;
	private static final int COLOR_CLOSED = 0xF0A500;

	private static final DateTimeFormatter CLOSE_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private EventEmbeds() {
	}

	/** Renders the current state of an event, including pools and implied odds. */
	public static MessageEmbed build(I18nService i18n, Locale locale, EventQueryService.EventView view,
			String currency, String symbol) {
		EmbedBuilder embed = new EmbedBuilder()
			.setColor(colorFor(view.status()))
			.setTitle(i18n.get(locale, DiscordMessages.EMBED_EVENT_TITLE, view.eventId(), view.title()));

		if (view.description() != null && !view.description().isBlank()) {
			embed.setDescription(view.description());
		}

		embed.addField(i18n.get(locale, DiscordMessages.EMBED_EVENT_OPTIONS), optionLines(i18n, locale, view,
			symbol), false);
		embed.addField(i18n.get(locale, DiscordMessages.EMBED_EVENT_POOL),
			view.totalPool().setScale(Money.SCALE, java.math.RoundingMode.HALF_UP).toPlainString() + " "
				+ currency + " · " + i18n.get(locale, DiscordMessages.EMBED_EVENT_NO_BETS, view.betCount()),
			true);
		embed.addField(i18n.get(locale, DiscordMessages.EMBED_EVENT_CLOSES), closeText(i18n, locale, view), true);

		if (view.winningOptionId() != null) {
			view.options().stream()
				.filter(option -> option.optionId() == view.winningOptionId())
				.findFirst()
				.ifPresent(winner -> embed.addField(i18n.get(locale, DiscordMessages.EMBED_EVENT_WINNER),
					winner.label(), false));
		}

		embed.setFooter(i18n.get(locale, DiscordMessages.EMBED_EVENT_FOOTER,
			i18n.get(locale, view.status().labelKey()), i18n.get(locale, view.payoutMode().labelKey())));
		return embed.build();
	}

	private static String optionLines(I18nService i18n, Locale locale, EventQueryService.EventView view,
			String symbol) {
		StringBuilder builder = new StringBuilder();
		for (EventQueryService.OptionView option : view.options()) {
			if (!builder.isEmpty()) {
				builder.append('\n');
			}
			builder.append("**").append(option.optionId()).append(".** ").append(option.label());
			builder.append(" — ").append(option.pool().setScale(Money.SCALE, java.math.RoundingMode.HALF_UP)
				.toPlainString()).append(' ').append(symbol);
			if (option.impliedOdds() != null && option.impliedOdds().signum() > 0) {
				builder.append(" · x").append(option.impliedOdds().toPlainString());
			}
		}
		return builder.isEmpty() ? i18n.get(locale, DiscordMessages.EMBED_EVENT_NO_BETS, 0) : builder.toString();
	}

	private static String closeText(I18nService i18n, Locale locale, EventQueryService.EventView view) {
		if (view.closesAt() == null) {
			return i18n.get(locale, DiscordMessages.EMBED_EVENT_NO_CLOSE);
		}
		return CLOSE_FORMAT.format(view.closesAt());
	}

	private static int colorFor(EventStatus status) {
		return switch (status) {
			case OPEN -> COLOR_OPEN;
			case CLOSED -> COLOR_CLOSED;
			case SETTLED -> COLOR_SETTLED;
			case CANCELLED -> COLOR_CANCELLED;
			default -> COLOR_CLOSED;
		};
	}

	/** Simple line describing one bet, used by {@code /bet mine} and {@code /bet list}. */
	public static String describeBet(I18nService i18n, Locale locale, BetEvent event,
			souris.botdealer.domain.Bet bet, String optionLabel, String symbol) {
		String status = i18n.get(locale, bet.getStatus().labelKey());
		String payout = bet.getStatus() == BetStatus.WON || bet.getStatus() == BetStatus.REFUNDED
			? " → " + symbol + " " + (bet.getPayout() == null ? "0.00" : bet.getPayout().toPlainString())
			: "";
		return "#" + event.getId() + " " + event.getTitle() + " · " + optionLabel + " · "
			+ symbol + " " + bet.getAmount().toPlainString() + " · " + status + payout;
	}

	/** Short one-line summary of an event for list replies. */
	public static String describeEvent(I18nService i18n, Locale locale, EventQueryService.EventView view,
			String currency) {
		return "#" + view.eventId() + " " + view.title() + " · "
			+ i18n.get(locale, view.status().labelKey()) + " · " + view.totalPool().toPlainString() + " "
			+ currency + " · " + view.options().size() + "x";
	}

	/** Whether the embed should be refreshed after a bet (only while it is live). */
	public static boolean isLive(EventStatus status) {
		return status == EventStatus.OPEN || status == EventStatus.CLOSED;
	}

	/** Payout mode badge for the event list. */
	public static String modeLabel(I18nService i18n, Locale locale, PayoutMode mode) {
		return i18n.get(locale, mode.labelKey());
	}

	/** Timestamp helper for tests. */
	public static Instant now() {
		return Instant.now();
	}

	/** Formats an amount with the guild symbol. */
	public static String amount(String symbol, BigDecimal amount) {
		return symbol + " " + amount.setScale(Money.SCALE, java.math.RoundingMode.HALF_UP).toPlainString();
	}
}
