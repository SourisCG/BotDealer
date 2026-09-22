/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import souris.botdealer.domain.BetEvent;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.repository.BetEventRepository;
import souris.botdealer.service.betting.EventQueryService;
import souris.botdealer.service.economy.GuildConfigService;

/**
 * Posts and refreshes the live event card in the channel where the event was created.
 *
 * <p>Uses {@link ObjectProvider} for the bot service on purpose: the bot depends on the
 * command dispatcher, which depends on the handlers, which end up here. Resolving the
 * JDA lazily at call time breaks that cycle and is also the honest semantic — the card
 * can only be posted while a session exists.</p>
 *
 * <p>Every Discord call is asynchronous ({@code queue()}); failures are logged, never
 * thrown, because a missing embed must not break the betting itself.</p>
 */
@Service
public class EventAnnouncer {

	private static final Logger log = LoggerFactory.getLogger(EventAnnouncer.class);

	private final ObjectProvider<DiscordBotService> bot;
	private final BetEventRepository events;
	private final EventQueryService queries;
	private final I18nService i18n;
	private final GuildConfigService guildConfig;

	public EventAnnouncer(ObjectProvider<DiscordBotService> bot, BetEventRepository events,
			EventQueryService queries, I18nService i18n, GuildConfigService guildConfig) {
		this.bot = bot;
		this.events = events;
		this.queries = queries;
		this.i18n = i18n;
		this.guildConfig = guildConfig;
	}

	/** Creates the card if it does not exist yet, otherwise refreshes it. */
	public void refresh(long eventId) {
		JDA jda = bot.getIfAvailable() == null ? null : bot.getObject().jda().orElse(null);
		if (jda == null) {
			return;
		}
		events.findById(eventId).ifPresent(event -> {
			if (event.getChannelId() == null) {
				return;
			}
			EventQueryService.EventView view = queries.toView(event);
			var embed = souris.botdealer.service.discord.commands.EventEmbeds.build(i18n,
				guildConfig.locale(event.getGuildId()), view,
				guildConfig.currencyPlural(event.getGuildId()),
				guildConfig.currencySymbol(event.getGuildId()));

			TextChannel channel = jda.getTextChannelById(event.getChannelId());
			if (channel == null) {
				log.debug("Channel {} is gone; skipping the event card", event.getChannelId());
				return;
			}
			if (event.getMessageId() == null) {
				channel.sendMessageEmbeds(embed).queue(
					message -> rememberMessage(eventId, message.getIdLong()),
					failure -> log.warn("Could not post the event card for #{}: {}", eventId,
						failure.getMessage()));
			} else {
				channel.editMessageEmbedsById(event.getMessageId(), embed).queue(
					success -> {
					},
					failure -> log.warn("Could not refresh the event card for #{}: {}", eventId,
						failure.getMessage()));
			}
		});
	}

	/** Sends a plain announcement to the event channel, if it is still reachable. */
	public void announce(long eventId, String text) {
		JDA jda = bot.getIfAvailable() == null ? null : bot.getObject().jda().orElse(null);
		if (jda == null) {
			return;
		}
		events.findById(eventId).ifPresent(event -> {
			if (event.getChannelId() == null) {
				return;
			}
			TextChannel channel = jda.getTextChannelById(event.getChannelId());
			if (channel != null) {
				channel.sendMessage(text).queue(
					success -> {
					},
					failure -> log.warn("Could not announce the result of #{}", eventId));
			}
		});
	}

	private void rememberMessage(long eventId, long messageId) {
		events.findById(eventId).ifPresent(event -> {
			BetEvent stored = event;
			stored.setMessageId(messageId);
			events.save(stored);
		});
	}
}
