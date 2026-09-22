/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

/**
 * Feeds LavaPlayer's Opus frames straight to Discord.
 *
 * <p>{@link #isOpus()} returns true because YouTube (and WebM in general) already
 * delivers Opus, which is exactly what Discord wants: LavaPlayer passes the frames
 * through without decoding and re-encoding. Changing the volume forces a re-encode,
 * which is why opus-java is bundled.</p>
 */
public class LavaplayerSendHandler implements AudioSendHandler {

	private final AudioPlayer player;
	private final MutableAudioFrame frame = new MutableAudioFrame();
	private final ByteBuffer buffer = ByteBuffer.allocate(1024);

	public LavaplayerSendHandler(AudioPlayer player) {
		this.player = player;
		frame.setBuffer(buffer);
	}

	@Override
	public boolean canProvide() {
		// Pulling here and returning the same frame from provide20MsAudio is the pattern
		// LavaPlayer expects: JDA calls canProvide() immediately before each provide.
		return player.provide(frame);
	}

	@Override
	public ByteBuffer provide20MsAudio() {
		((Buffer) buffer).flip();
		return buffer;
	}

	@Override
	public boolean isOpus() {
		return true;
	}
}
