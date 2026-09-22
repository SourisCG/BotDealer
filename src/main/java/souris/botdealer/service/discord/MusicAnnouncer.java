/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.music.MusicEvents;

/**
 * Posts what is playing, and why something failed, in the channel where the request was
 * made.
 *
 * <p>Playback resolution is asynchronous, so the command cannot answer directly: it
 * defers and this announcer reports the outcome. It listens to {@link UiEventBus}, which
 * marshals to the JavaFX thread; the Discord calls themselves are asynchronous
 * ({@code queue()}), so nothing blocks the UI.</p>
 */
@Service
public class MusicAnnouncer {

	private static final Logger log = LoggerFactory.getLogger(MusicAnnouncer.class);

	private final ObjectProvider<DiscordBotService> bot;
	private final I18nService i18n;
	private final GuildConfigService guildConfig;

	/** Last channel a music command came from, per guild. */
	private final Map<Long, Long> lastChannel = new ConcurrentHashMap<>();

	public MusicAnnouncer(ObjectProvider<DiscordBotService> bot, I18nService i18n,
			GuildConfigService guildConfig, UiEventBus uiEvents) {
		this.bot = bot;
		this.i18n = i18n;
		this.guildConfig = guildConfig;
		uiEvents.subscribe(this::onEvent);
	}

	/** Remembers where to report results for a guild. */
	public void rememberChannel(long guildId, long channelId) {
		lastChannel.put(guildId, channelId);
	}

	private void onEvent(Object event) {
		if (event instanceof MusicEvents.TrackStarted started) {
			send(started.guildId(), null, i18n.get(locale(started.guildId()), "music.announce.nowPlaying",
				started.title(), started.author()));
		} else if (event instanceof MusicEvents.PlaybackFailed failure) {
			String message = i18n.get(locale(failure.guildId()), failure.messageKey(),
				failure.detail().isBlank() ? new Object[0] : new Object[] {failure.detail()});
			send(failure.guildId(), null, message);
		}
	}

	private java.util.Locale locale(long guildId) {
		return guildConfig.locale(guildId);
	}

	private void send(long guildId, String ignored, String text) {
		Long channelId = lastChannel.get(guildId);
		if (channelId == null) {
			return;
		}
		DiscordBotService service = bot.getIfAvailable();
		JDA jda = service == null ? null : service.jda().orElse(null);
		if (jda == null) {
			return;
		}
		TextChannel channel = jda.getTextChannelById(channelId);
		if (channel == null) {
			return;
		}
		channel.sendMessage(text).queue(
			success -> {
			},
			failure -> log.debug("Could not announce music state in guild {}", guildId));
	}
}
