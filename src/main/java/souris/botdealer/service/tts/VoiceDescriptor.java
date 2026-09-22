/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A Piper voice on disk: the model plus its configuration.
 *
 * @param id           stable identifier (the file name without {@code .onnx})
 * @param displayName  friendly name for the UI, from the model metadata
 * @param languageCode e.g. {@code es_ES}, used to group voices
 * @param languageName human-readable language, e.g. {@code Español}
 * @param model        the {@code .onnx} file
 * @param config       the {@code .onnx.json} file
 * @param sampleRate   from the metadata; needed to write a valid WAV
 * @param speakers     speaker names mapped to their ids, empty for single-speaker voices
 * @param sizeBytes    total size of both files, for the library view
 */
public record VoiceDescriptor(String id, String displayName, String languageCode, String languageName,
		Path model, Path config, int sampleRate, Map<Long, String> speakers, long sizeBytes) {

	public boolean isMultiSpeaker() {
		return speakers.size() > 1;
	}

	public List<String> speakerNames() {
		return List.copyOf(speakers.values());
	}

	/** Speaker id to use when none was chosen: the first one, or 0. */
	public long defaultSpeakerId() {
		return speakers.keySet().stream().sorted().findFirst().orElse(0L);
	}

	/** Groups voices by language for the picker. */
	public String languageLabel() {
		return languageName == null || languageName.isBlank() ? languageCode : languageName;
	}
}
