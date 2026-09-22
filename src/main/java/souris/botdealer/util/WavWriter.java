/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.util;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes 16-bit mono PCM WAV files.
 *
 * <p>Piper returns raw {@code short[]} samples; LavaPlayer's WAV probe can read them
 * straight from a file, which is how synthesized speech reaches Discord without pulling
 * in an audio conversion step.</p>
 */
public final class WavWriter {

	private static final int HEADER_BYTES = 44;
	private static final int BITS_PER_SAMPLE = 16;
	private static final int CHANNELS = 1;

	private WavWriter() {
	}

	public static void writeMono16(Path file, short[] samples, int sampleRate) throws IOException {
		if (samples == null || samples.length == 0) {
			throw new IOException("Refusing to write an empty audio file");
		}
		if (sampleRate <= 0) {
			throw new IOException("Invalid sample rate: " + sampleRate);
		}
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		byte[] data = new byte[samples.length * 2];
		for (int index = 0; index < samples.length; index++) {
			data[index * 2] = (byte) (samples[index] & 0xFF);
			data[index * 2 + 1] = (byte) ((samples[index] >> 8) & 0xFF);
		}

		int byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8;
		int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;

		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
			out.writeBytes("RIFF");
			out.writeInt(Integer.reverseBytes(HEADER_BYTES - 8 + data.length));
			out.writeBytes("WAVE");
			out.writeBytes("fmt ");
			out.writeInt(Integer.reverseBytes(16));            // PCM header size
			out.writeShort(Short.reverseBytes((short) 1));     // format: PCM
			out.writeShort(Short.reverseBytes((short) CHANNELS));
			out.writeInt(Integer.reverseBytes(sampleRate));
			out.writeInt(Integer.reverseBytes(byteRate));
			out.writeShort(Short.reverseBytes((short) blockAlign));
			out.writeShort(Short.reverseBytes((short) BITS_PER_SAMPLE));
			out.writeBytes("data");
			out.writeInt(Integer.reverseBytes(data.length));
			out.write(data);
		}
	}

	/** Duration of a 16-bit mono WAV in milliseconds. */
	public static long durationMillis(int sampleCount, int sampleRate) {
		return sampleRate <= 0 ? 0 : (long) sampleCount * 1000 / sampleRate;
	}
}
