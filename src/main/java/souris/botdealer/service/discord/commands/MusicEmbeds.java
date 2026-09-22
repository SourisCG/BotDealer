/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.util.Locale;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.music.RepeatMode;

/**
 * Now-playing and queue cards.
 *
 * <p>Uses the thumbnail the YouTube source provides when available, and renders the
 * progress as a plain bar so it reads well on every client.</p>
 */
public final class MusicEmbeds {

	private static final int COLOR_MUSIC = 0x38A3A5;
	private static final int PROGRESS_WIDTH = 16;

	private MusicEmbeds() {
	}

	public static MessageEmbed nowPlaying(I18nService i18n, Locale locale, AudioTrack track, boolean paused,
			int volume, RepeatMode repeat, int queueSize) {
		var info = track.getInfo();
		EmbedBuilder embed = new EmbedBuilder()
			.setColor(COLOR_MUSIC)
			.setTitle(i18n.get(locale, paused ? "music.embed.paused" : "music.embed.playing"))
			.setDescription("**" + info.title + "**\n" + info.author);

		embed.addField(i18n.get(locale, "music.embed.progress"),
			progressBar(track) + "\n" + formatDuration(track.getPosition()) + " / "
				+ (info.isStream ? i18n.get(locale, "music.embed.live") : formatDuration(info.length)),
			false);
		embed.addField(i18n.get(locale, "music.embed.volume"), volume + "%", true);
		embed.addField(i18n.get(locale, "music.embed.repeat"), i18n.get(locale, repeat.labelKey()), true);
		embed.addField(i18n.get(locale, "music.embed.queue"), Integer.toString(queueSize), true);

		if (info.artworkUrl != null && !info.artworkUrl.isBlank()) {
			embed.setThumbnail(info.artworkUrl);
		}
		return embed.build();
	}

	/** One line per queued track, numbered from 1 like {@code /queue} shows them. */
	public static String queueList(I18nService i18n, Locale locale, java.util.List<AudioTrack> queue,
			int limit) {
		if (queue.isEmpty()) {
			return i18n.get(locale, "music.reply.queueEmpty");
		}
		StringBuilder builder = new StringBuilder();
		int index = 1;
		for (AudioTrack track : queue) {
			if (index > limit) {
				builder.append('\n').append(i18n.get(locale, "music.reply.queueMore", queue.size() - limit));
				break;
			}
			builder.append(index++).append(". **").append(track.getInfo().title).append("** · ")
				.append(formatDuration(track.getDuration())).append('\n');
		}
		return builder.toString();
	}

	/** {@code mm:ss}, or {@code h:mm:ss} for long tracks. */
	public static String formatDuration(long millis) {
		if (millis <= 0) {
			return "0:00";
		}
		long totalSeconds = millis / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		if (hours > 0) {
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format("%d:%02d", minutes, seconds);
	}

	/** A text progress bar; a stream shows as full. */
	private static String progressBar(AudioTrack track) {
		var info = track.getInfo();
		if (info.isStream || info.length <= 0) {
			return "\u25AC".repeat(PROGRESS_WIDTH);
		}
		int filled = (int) Math.min(PROGRESS_WIDTH,
			Math.round(PROGRESS_WIDTH * (double) track.getPosition() / info.length));
		return "\u25AC".repeat(Math.max(0, filled)) + "\u25B0".repeat(Math.max(0, PROGRESS_WIDTH - filled));
	}
}
