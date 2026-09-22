/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Turns a Discord message into something worth reading aloud.
 *
 * <p>Raw message content makes for terrible speech: mentions arrive as {@code <@1234>},
 * custom emoji as {@code <a:party:5678>}, and links are read out character by character.
 * This class removes those, collapses whitespace and caps the length so one person cannot
 * monopolise the voice channel.</p>
 */
@Component
public class TtsTextSanitizer {

	/** {@code <@123>}, {@code <@!123>}, {@code <@&123>}, {@code <#123>}. */
	private static final Pattern MENTION = Pattern.compile("<[@#][!&]?\\d+>");

	/** {@code <:name:id>} and animated {@code <a:name:id>}. */
	private static final Pattern CUSTOM_EMOJI = Pattern.compile("<a?:[A-Za-z0-9_]+:\\d+>");

	/** Plain http(s) links, optionally wrapped in Discord's angle brackets. */
	private static final Pattern URL = Pattern.compile("<https?://\\S+>|https?://\\S+");

	/** Characters Discord uses for formatting; they are noise when spoken. */
	private static final Pattern MARKDOWN = Pattern.compile("[*_~`|]");

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	/** Default cap for a single utterance. */
	public static final int DEFAULT_MAX_LENGTH = 300;

	/** Result of cleaning, so callers can explain why nothing was spoken. */
	public record Result(String text, boolean empty) {
	}

	public Result sanitize(String raw) {
		return sanitize(raw, DEFAULT_MAX_LENGTH, true);
	}

	/**
	 * @param maxLength    hard cap; longer text is truncated on a word boundary
	 * @param stripLinks   whether links are dropped instead of read out
	 */
	public Result sanitize(String raw, int maxLength, boolean stripLinks) {
		if (raw == null || raw.isBlank()) {
			return new Result("", true);
		}
		String text = raw;
		text = MENTION.matcher(text).replaceAll("");
		text = CUSTOM_EMOJI.matcher(text).replaceAll("");
		if (stripLinks) {
			text = URL.matcher(text).replaceAll("");
		}
		text = MARKDOWN.matcher(text).replaceAll("");
		text = WHITESPACE.matcher(text).replaceAll(" ").strip();

		if (text.isEmpty()) {
			return new Result("", true);
		}
		if (text.length() > maxLength) {
			text = truncateOnWordBoundary(text, maxLength);
		}
		return new Result(text, text.isEmpty());
	}

	/** Keeps whole words, so the speech does not stop mid-syllable. */
	private static String truncateOnWordBoundary(String text, int maxLength) {
		int cut = text.lastIndexOf(' ', maxLength);
		if (cut < maxLength / 2) {
			cut = maxLength;
		}
		return text.substring(0, cut).strip();
	}

	/** True when the message is only mentions, emoji or links. */
	public boolean isSpeakable(String raw) {
		return !sanitize(raw).empty();
	}
}
