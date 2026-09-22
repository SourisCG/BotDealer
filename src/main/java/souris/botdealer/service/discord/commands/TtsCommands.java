/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.discord.DiscordMessages;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.music.MusicService;
import souris.botdealer.service.tts.TtsService;
import souris.botdealer.service.tts.VoiceDescriptor;
import souris.botdealer.service.tts.VoiceRegistry;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

/** {@code /tts join|leave|say|voice|on|off|status}. */
@Component
public class TtsCommands extends AbstractCommandHandler {

	public static final String ROOT = "tts";

	private final TtsService tts;
	private final VoiceRegistry voices;
	private final MusicService music;
	private final AppSettingsService settings;

	public TtsCommands(I18nService i18n, GuildConfigService guildConfig, TtsService tts, VoiceRegistry voices,
			MusicService music, AppSettingsService settings) {
		super(i18n, guildConfig);
		this.tts = tts;
		this.voices = voices;
		this.music = music;
		this.settings = settings;
	}

	@Override
	public List<CommandData> definitions() {
		return List.of(Commands.slash(ROOT, DiscordMessages.CMD_TTS)
			.addSubcommands(
				new SubcommandData("say", DiscordMessages.CMD_TTS_SAY)
					.addOptions(new OptionData(OptionType.STRING, "text", DiscordMessages.OPT_TTS_TEXT, true)),
				new SubcommandData("join", DiscordMessages.CMD_TTS_JOIN),
				new SubcommandData("leave", DiscordMessages.CMD_TTS_LEAVE),
				new SubcommandData("voice", DiscordMessages.CMD_TTS_VOICE)
					.addOptions(new OptionData(OptionType.STRING, "name", DiscordMessages.OPT_TTS_VOICE, false)),
				new SubcommandData("on", DiscordMessages.CMD_TTS_ON),
				new SubcommandData("off", DiscordMessages.CMD_TTS_OFF),
				new SubcommandData("status", DiscordMessages.CMD_TTS_STATUS)));
	}

	@Override
	public void handle(SlashCommandInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		long userId = event.getUser().getIdLong();

		switch (String.valueOf(event.getSubcommandName())) {
			case "say" -> {
				event.reply(text(event, DiscordMessages.REPLY_TTS_SAY)).setEphemeral(true).queue();
				tts.speak(guildId, event.getOption("text").getAsString());
			}
			case "join" -> {
				var member = event.getMember();
				var channel = member == null || member.getVoiceState() == null ? null
					: member.getVoiceState().getChannel();
				if (channel == null) {
					event.reply(text(event, "tts.error.joinVoiceFirst")).setEphemeral(true).queue();
					return;
				}
				music.join(guildId, channel.getIdLong());
				event.reply(text(event, "tts.reply.joined", channel.getName())).setEphemeral(true).queue();
			}
			case "leave" -> {
				music.leave(guildId);
				event.reply(text(event, "tts.reply.left")).setEphemeral(true).queue();
			}
			case "voice" -> selectVoice(event);
			case "on" -> {
				settings.set(AppSettingKey.TTS_READ_ALOUD, "true");
				event.reply(text(event, "tts.reply.readAloudOn")).setEphemeral(true).queue();
			}
			case "off" -> {
				settings.set(AppSettingKey.TTS_READ_ALOUD, "false");
				event.reply(text(event, "tts.reply.readAloudOff")).setEphemeral(true).queue();
			}
			default -> status(event, guildId);
		}
	}

	private void selectVoice(SlashCommandInteractionEvent event) {
		String requested = event.getOption("name") == null ? null : event.getOption("name").getAsString();
		if (requested == null || requested.isBlank()) {
			StringBuilder builder = new StringBuilder(text(event, "tts.reply.voiceList")).append('\n');
			voices.voices().forEach(voice -> builder.append("• `").append(voice.id()).append("` — ")
				.append(voice.languageLabel()).append('\n'));
			event.reply(builder.toString()).setEphemeral(true).queue();
			return;
		}
		var match = voices.find(requested.strip());
		if (match.isEmpty()) {
			event.reply(text(event, "tts.error.unknownVoice", requested)).setEphemeral(true).queue();
			return;
		}
		settings.set(AppSettingKey.TTS_VOICE, match.get().id());
		event.reply(text(event, "tts.reply.voiceSet", match.get().id())).setEphemeral(true).queue();
	}

	private void status(SlashCommandInteractionEvent event, long guildId) {
		var voice = tts.voiceFor(guildId).map(VoiceDescriptor::id).orElse("-");
		event.reply(text(event, "tts.reply.status", tts.isReady() ? text(event, "tts.reply.ready")
			: text(event, "tts.reply.notReady"), voice,
			settings.getBoolean(AppSettingKey.TTS_READ_ALOUD) ? text(event, "common.on")
				: text(event, "common.off"))).setEphemeral(true).queue();
	}
}
