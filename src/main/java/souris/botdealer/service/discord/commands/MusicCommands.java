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
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.discord.DiscordMessages;
import souris.botdealer.service.discord.MusicAnnouncer;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.music.MusicService;
import souris.botdealer.service.music.RepeatMode;

/**
 * Music commands.
 *
 * <p>One root command per action ({@code /play}, {@code /skip}, ...) because that is
 * what people type naturally; a single {@code /music} root with ten subcommands would
 * mean two extra keystrokes and worse autocomplete.</p>
 *
 * <p>Control commands require {@link MusicService#canControl}: Manage Server, the
 * configured DJ role, or simply being in the channel the bot is playing in. Playback
 * itself only requires being in a voice channel.</p>
 */
@Component
public class MusicCommands extends AbstractCommandHandler {

	private static final int QUEUE_PREVIEW = 15;

	private final MusicService music;
	private final MusicAnnouncer announcer;

	public MusicCommands(I18nService i18n, GuildConfigService guildConfig, MusicService music,
			MusicAnnouncer announcer) {
		super(i18n, guildConfig);
		this.music = music;
		this.announcer = announcer;
	}

	@Override
	public List<CommandData> definitions() {
		return List.of(
			Commands.slash("play", DiscordMessages.CMD_PLAY)
				.addOptions(new OptionData(OptionType.STRING, "query", DiscordMessages.OPT_QUERY, true)),
			Commands.slash("pause", DiscordMessages.CMD_PAUSE),
			Commands.slash("resume", DiscordMessages.CMD_RESUME),
			Commands.slash("skip", DiscordMessages.CMD_SKIP),
			Commands.slash("stop", DiscordMessages.CMD_STOP),
			Commands.slash("queue", DiscordMessages.CMD_QUEUE),
			Commands.slash("nowplaying", DiscordMessages.CMD_NOWPLAYING),
			Commands.slash("volume", DiscordMessages.CMD_VOLUME)
				.addOptions(new OptionData(OptionType.INTEGER, "level", DiscordMessages.OPT_LEVEL, true)
					.setMinValue(0).setMaxValue(200)),
			Commands.slash("loop", DiscordMessages.CMD_LOOP)
				.addOptions(new OptionData(OptionType.STRING, "mode", DiscordMessages.OPT_LOOP_MODE, false)
					.addChoice("off", "OFF").addChoice("track", "TRACK").addChoice("queue", "QUEUE")),
			Commands.slash("shuffle", DiscordMessages.CMD_SHUFFLE),
			Commands.slash("disconnect", DiscordMessages.CMD_DISCONNECT));
	}

	@Override
	public void handle(SlashCommandInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		long userId = event.getUser().getIdLong();
		announcer.rememberChannel(guildId, event.getChannel().getIdLong());

		switch (event.getName()) {
			case "play" -> play(event, guildId, userId);
			case "pause" -> control(event, guildId, userId, () -> music.pause(guildId) ? "music.reply.paused"
				: "music.reply.nothingPlaying");
			case "resume" -> control(event, guildId, userId, () -> music.resume(guildId) ? "music.reply.resumed"
				: "music.reply.nothingPaused");
			case "skip" -> control(event, guildId, userId, () -> music.skip(guildId) ? "music.reply.skipped"
				: "music.reply.nothingPlaying");
			case "stop" -> control(event, guildId, userId, () -> {
				music.stop(guildId);
				return "music.reply.stopped";
			});
			case "queue" -> event.reply(MusicEmbeds.queueList(i18n, locale(event), music.queue(guildId),
				QUEUE_PREVIEW)).setEphemeral(true).queue();
			case "nowplaying" -> nowPlaying(event, guildId);
			case "volume" -> control(event, guildId, userId, () -> {
				int level = event.getOption("level").getAsInt();
				music.setVolume(guildId, level);
				return i18n.get(locale(event), "music.reply.volumeSet", level);
			});
			case "loop" -> control(event, guildId, userId, () -> {
				RepeatMode mode = event.getOption("mode") == null
					? music.cycleRepeat(guildId)
					: setRepeat(guildId, event.getOption("mode").getAsString());
				return i18n.get(locale(event), "music.reply.repeatSet", i18n.get(locale(event), mode.labelKey()));
			});
			case "shuffle" -> control(event, guildId, userId, () -> i18n.get(locale(event),
				music.toggleShuffle(guildId) ? "music.reply.shuffleOn" : "music.reply.shuffleOff"));
			case "disconnect" -> control(event, guildId, userId, () -> {
				music.leave(guildId);
				return "music.reply.disconnected";
			});
			default -> event.reply(text(event, DiscordMessages.ERROR_GENERIC)).setEphemeral(true).queue();
		}
	}

	private void play(SlashCommandInteractionEvent event, long guildId, long userId) {
		String query = event.getOption("query").getAsString();
		event.reply(text(event, DiscordMessages.REPLY_SEARCHING)).setEphemeral(true).queue();
		// Resolution is asynchronous: the result arrives as a MusicEvent and the
		// announcer posts what is playing (or why it failed) in the channel.
		music.play(guildId, userId, query);
	}

	private void nowPlaying(SlashCommandInteractionEvent event, long guildId) {
		music.nowPlaying(guildId).ifPresentOrElse(
			track -> event.replyEmbeds(MusicEmbeds.nowPlaying(i18n, locale(event), track,
				music.isPaused(guildId), music.volume(guildId), music.repeat(guildId),
				music.queue(guildId).size())).setEphemeral(true).queue(),
			() -> event.reply(text(event, "music.reply.nothingPlaying")).setEphemeral(true).queue());
	}

	private RepeatMode setRepeat(long guildId, String mode) {
		RepeatMode target = RepeatMode.valueOf(mode);
		while (music.repeat(guildId) != target) {
			music.cycleRepeat(guildId);
		}
		return target;
	}

	/** Runs a control action, refusing it when the member may not control playback. */
	private void control(SlashCommandInteractionEvent event, long guildId, long userId,
			java.util.function.Supplier<String> action) {
		if (!music.canControl(guildId, userId)) {
			event.reply(text(event, "music.error.notAllowed")).setEphemeral(true).queue();
			return;
		}
		String result = action.get();
		String reply = result.startsWith("music.reply.") ? text(event, result) : result;
		event.reply(reply).setEphemeral(true).queue();
	}
}
