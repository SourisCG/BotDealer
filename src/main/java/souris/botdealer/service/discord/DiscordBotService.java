/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

/**
 * Owns the JDA session: connect, disconnect, and report state to the UI.
 *
 * <p>The token is read from the OS keychain at connect time and never stored in a field
 * or logged. {@link #start()} blocks until Discord confirms the connection (JDA
 * behaviour), so the UI calls {@link #startAsync()} instead.</p>
 */
@Service
public class DiscordBotService {

	private static final Logger log = LoggerFactory.getLogger(DiscordBotService.class);

	private final SecretService secrets;
	private final AppSettingsService settings;
	private final CommandDispatcher dispatcher;
	private final UiEventBus uiEvents;

	private final AtomicReference<BotStatus> status = new AtomicReference<>(BotStatus.STOPPED);
	private volatile String lastErrorKey = "";
	private volatile JDA jda;
	private volatile boolean voiceAvailable;

	public DiscordBotService(SecretService secrets, AppSettingsService settings, CommandDispatcher dispatcher,
			UiEventBus uiEvents) {
		this.secrets = secrets;
		this.settings = settings;
		this.dispatcher = dispatcher;
		this.uiEvents = uiEvents;
	}

	// ------------------------------------------------------------------ lifecycle

	/** Connects on a background virtual thread and returns immediately. */
	public void startAsync() {
		Thread.ofVirtual().name("discord-start").start(() -> {
			try {
				start();
			} catch (BotStartException e) {
				log.warn("Bot start failed: {}", e.messageKey());
			}
		});
	}

	/** Connects and waits for the ready event. Call off the JavaFX thread. */
	public synchronized void start() {
		if (isConnected()) {
			throw new BotStartException(DiscordMessages.BOT_ERROR_ALREADY_RUNNING);
		}
		String token = secrets.get(SecretKey.DISCORD_TOKEN)
			.orElseThrow(() -> new BotStartException(DiscordMessages.BOT_ERROR_NO_TOKEN));

		setStatus(BotStatus.STARTING, "");
		try {
			JDABuilder builder = JDABuilder.createDefault(token, gatewayIntents())
				.enableCache(CacheFlag.VOICE_STATE)
				.setActivity(Activity.listening("chorizos"))
				.addEventListeners(dispatcher);

			AudioModuleConfig audio = audioConfig();
			if (audio != null) {
				builder.setAudioModuleConfig(audio);
			}

			jda = builder.build();
			voiceAvailable = audio != null;
			setStatus(BotStatus.CONNECTED, "");
			log.info("Bot connected as {} ({} guilds, voice {})",
				jda.getSelfUser().getName(), jda.getGuilds().size(), voiceAvailable ? "on" : "off");
		} catch (InvalidTokenException e) {
			jda = null;
			setStatus(BotStatus.ERROR, DiscordMessages.BOT_ERROR_INVALID_TOKEN);
			throw new BotStartException(DiscordMessages.BOT_ERROR_INVALID_TOKEN, e);
		} catch (Exception e) {
			jda = null;
			String key = classifyFailure(e);
			setStatus(BotStatus.ERROR, key);
			throw new BotStartException(key, e);
		}
	}

	public synchronized void stop() {
		JDA current = jda;
		jda = null;
		if (current != null) {
			log.info("Disconnecting the bot");
			current.shutdown();
		}
		voiceAvailable = false;
		setStatus(BotStatus.STOPPED, "");
	}

	public synchronized void restart() {
		stop();
		start();
	}

	// ------------------------------------------------------------------ state

	public BotStatus status() {
		return status.get();
	}

	public String lastErrorKey() {
		return lastErrorKey;
	}

	public boolean isConnected() {
		JDA current = jda;
		return current != null && current.getStatus() == JDA.Status.CONNECTED;
	}

	/** False when the DAVE/udpqueue natives could not load, so music and TTS are off. */
	public boolean isVoiceAvailable() {
		return voiceAvailable;
	}

	public Optional<JDA> jda() {
		return Optional.ofNullable(jda);
	}

	public Optional<SelfUser> selfUser() {
		return jda().map(JDA::getSelfUser);
	}

	public List<Guild> guilds() {
		return jda().map(JDA::getGuilds).orElse(List.of());
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Intents the bot needs. {@code MESSAGE_CONTENT} is privileged: requesting it while
	 * it is disabled in the Developer Portal makes the whole login fail, so it is only
	 * asked for when read-aloud TTS is enabled.
	 */
	private Set<GatewayIntent> gatewayIntents() {
		Set<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MESSAGES,
			GatewayIntent.GUILD_VOICE_STATES);
		if (settings.getBoolean(AppSettingKey.TTS_READ_ALOUD)) {
			intents.add(GatewayIntent.MESSAGE_CONTENT);
		}
		return intents;
	}

	/**
	 * Voice needs the DAVE encryption implementation plus the native send loop. If the
	 * natives cannot load (unsupported platform) the bot still connects, just without
	 * voice, instead of refusing to start.
	 */
	private AudioModuleConfig audioConfig() {
		try {
			return new AudioModuleConfig()
				.withDaveSessionFactory(new JDaveSessionFactory())
				.withAudioSendFactory(new NativeAudioSendFactory());
		} catch (Throwable t) {
			log.warn("Discord voice unavailable, music and TTS will be disabled: {}", t.toString());
			return null;
		}
	}

	private String classifyFailure(Exception e) {
		String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
		if (message.contains("disallowed intent") || message.contains("privileged")
				|| message.contains("message content")) {
			return DiscordMessages.BOT_ERROR_INTENT;
		}
		if (e instanceof java.io.IOException || message.contains("timed out") || message.contains("unknown host")
				|| message.contains("connection")) {
			return DiscordMessages.BOT_ERROR_NETWORK;
		}
		return DiscordMessages.BOT_ERROR_NETWORK;
	}

	private void setStatus(BotStatus newStatus, String detailKey) {
		status.set(newStatus);
		lastErrorKey = detailKey;
		uiEvents.publish(new UiEvents.BotStatusChanged(newStatus, detailKey));
	}
}
