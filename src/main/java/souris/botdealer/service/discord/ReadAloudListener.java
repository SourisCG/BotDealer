/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import souris.botdealer.service.tts.TtsService;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

/**
 * Reads messages aloud in the voice channel's own text chat.
 *
 * <p>Deliberately narrow: only messages written in the text chat of the voice channel the
 * bot is currently in are spoken, and only from members who are in that same channel. A
 * bot that reads every channel of a server is unbearable, and this rule is easy to
 * explain. Anything else goes through {@code /tts say}.</p>
 *
 * <p>Requires the privileged <b>Message Content</b> intent: without it JDA does not
 * deliver message text at all. The bot only requests that intent when read-aloud is
 * enabled, and the connection fails with a clear explanation if it is not enabled in the
 * Developer Portal.</p>
 */
@Component
public class ReadAloudListener extends ListenerAdapter {

	private static final Logger log = LoggerFactory.getLogger(ReadAloudListener.class);

	private final TtsService tts;
	private final AppSettingsService settings;
	private final ObjectProvider<DiscordBotService> bot;

	public ReadAloudListener(TtsService tts, AppSettingsService settings,
			ObjectProvider<DiscordBotService> bot) {
		this.tts = tts;
		this.settings = settings;
		this.bot = bot;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (!event.isFromGuild() || event.getAuthor().isBot()) {
			return;
		}
		if (!settings.getBoolean(AppSettingKey.TTS_READ_ALOUD)) {
			return;
		}
		String content = event.getMessage().getContentRaw();
		if (content.isBlank() || content.startsWith("/") || content.startsWith("!")) {
			return;
		}

		Guild guild = event.getGuild();
		AudioChannel botChannel = guild.getAudioManager().getConnectedChannel();
		if (botChannel == null) {
			return;
		}
		// Voice channels have their own text chat, and its id matches the channel's.
		if (event.getChannel().getIdLong() != botChannel.getIdLong()) {
			return;
		}
		Member author = event.getMember();
		if (author == null || author.getVoiceState() == null || author.getVoiceState().getChannel() == null
				|| author.getVoiceState().getChannel().getIdLong() != botChannel.getIdLong()) {
			return;
		}

		log.debug("Reading a message aloud in guild {}", guild.getIdLong());
		tts.speak(guild.getIdLong(), content);
	}
}
