/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import souris.botdealer.service.music.MusicService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

/**
 * Turns text into speech and puts it into the guild's queue.
 *
 * <p>Speech shares the music queue, so it ducks the music instead of fighting it: the
 * volume drops while the phrase plays and is restored a moment after the clip ends.</p>
 *
 * <p>Synthesis is slow (hundreds of milliseconds for a sentence) so it happens on a
 * background thread; callers get their answer through {@link TtsEvents}.</p>
 */
@Service
public class TtsService {

	private static final Logger log = LoggerFactory.getLogger(TtsService.class);

	/** Margin added before restoring the volume, so the last syllable is not clipped. */
	private static final long UNDUCK_MARGIN_MILLIS = 400;

	private final PiperTtsEngine engine;
	private final VoiceRegistry voices;
	private final TtsTextSanitizer sanitizer;
	private final MusicService music;
	private final AppSettingsService settings;
	private final UiEventBus uiEvents;
	private final ScheduledExecutorService scheduler;

	public TtsService(PiperTtsEngine engine, VoiceRegistry voices, TtsTextSanitizer sanitizer,
			MusicService music, AppSettingsService settings, UiEventBus uiEvents,
			ScheduledExecutorService scheduler) {
		this.engine = engine;
		this.voices = voices;
		this.sanitizer = sanitizer;
		this.music = music;
		this.settings = settings;
		this.uiEvents = uiEvents;
		this.scheduler = scheduler;
	}

	/** True when speech is enabled and the native engine loaded. */
	public boolean isReady() {
		return engine.isAvailable() && !voices.voices().isEmpty();
	}

	public String unavailableReason() {
		return engine.isAvailable()
			? (voices.voices().isEmpty() ? "tts.error.noVoice" : "")
			: engine.unavailableReason();
	}

	/** The voice configured for a guild, falling back to the app default. */
	public Optional<VoiceDescriptor> voiceFor(long guildId) {
		String configured = settings.get(AppSettingKey.TTS_VOICE);
		Optional<VoiceDescriptor> chosen = voices.find(configured);
		return chosen.isPresent() ? chosen : voices.defaultVoice();
	}

	public Optional<VoiceDescriptor> voices_() {
		return voices.findAny();
	}

	/**
	 * Speaks on behalf of the desktop operator or a command.
	 *
	 * @param text raw text; it is sanitized here
	 * @return false when nothing could be queued (and the reason was published)
	 */
	public boolean speak(long guildId, String text) {
		if (!settings.getBoolean(AppSettingKey.TTS_ENABLED)) {
			uiEvents.publish(new TtsEvents.SpeechFailed(guildId, "tts.error.disabled", ""));
			return false;
		}
		if (!engine.isAvailable()) {
			uiEvents.publish(new TtsEvents.SpeechFailed(guildId, "tts.error.engineUnavailable",
				engine.unavailableReason()));
			return false;
		}
		Optional<VoiceDescriptor> voice = voiceFor(guildId);
		if (voice.isEmpty()) {
			uiEvents.publish(new TtsEvents.SpeechFailed(guildId, "tts.error.noVoice", ""));
			return false;
		}
		var cleaned = sanitizer.sanitize(text, maxLength(), true);
		if (cleaned.empty()) {
			uiEvents.publish(new TtsEvents.SpeechFailed(guildId, "tts.error.emptyText", ""));
			return false;
		}
		if (!music.isConnected(guildId)) {
			uiEvents.publish(new TtsEvents.SpeechFailed(guildId, "tts.error.notConnected", ""));
			return false;
		}

		double speed = settings.getDouble(AppSettingKey.TTS_SPEED);
		Thread.ofVirtual().name("tts-synthesize").start(() ->
			synthesizeAndEnqueue(guildId, cleaned.text(), voice.get(), speed));
		return true;
	}

	/** Synthesizes to a WAV file without playing it; used by the export button. */
	public Path synthesizeToFile(String text, VoiceDescriptor voice) {
		var cleaned = sanitizer.sanitize(text, maxLength(), true);
		if (cleaned.empty()) {
			throw new TtsSynthesisException("tts.error.emptyText");
		}
		return engine.synthesize(cleaned.text(), voice, settings.getDouble(AppSettingKey.TTS_SPEED));
	}

	public int clearCache() {
		return engine.clearCache();
	}

	// ------------------------------------------------------------------ internals

	private void synthesizeAndEnqueue(long guildId, String text, VoiceDescriptor voice, double speed) {
		try {
			Path wav = engine.synthesize(text, voice, speed);
			boolean duck = settings.getBoolean(AppSettingKey.TTS_DUCK);
			int previousVolume = music.volume(guildId);
			if (duck) {
				int ducked = Math.max(1, previousVolume * settings.getInt(AppSettingKey.TTS_DUCK_PERCENT) / 100);
				music.setVolume(guildId, ducked);
			}
			music.enqueueLocalFile(guildId, wav, track -> {
				uiEvents.publish(new TtsEvents.SpeechQueued(guildId, text, track.getDuration()));
				if (duck) {
					scheduler.schedule(() -> music.setVolume(guildId, previousVolume),
						track.getDuration() + UNDUCK_MARGIN_MILLIS, TimeUnit.MILLISECONDS);
				}
			});
		} catch (TtsSynthesisException e) {
			log.warn("Speech failed for guild {}: {}", guildId, e.messageKey());
			uiEvents.publish(new TtsEvents.SpeechFailed(guildId, e.messageKey(), ""));
		} catch (Exception e) {
			log.error("Unexpected speech failure in guild {}: {}", guildId, e.toString());
			uiEvents.publish(new TtsEvents.SpeechFailed(guildId, "tts.error.failed", ""));
		}
	}

	private int maxLength() {
		int configured = settings.getInt(AppSettingKey.TTS_MAX_LENGTH);
		return configured > 0 ? configured : TtsTextSanitizer.DEFAULT_MAX_LENGTH;
	}
}
