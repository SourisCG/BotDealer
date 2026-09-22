# Text to speech

> 🇪🇸 **Resumen:** la voz funciona sin conexión con Piper, incluido en la app. Hay tres
> voces instaladas (español e inglés) y podés descargar más. La lectura en voz alta lee
> solo el chat del canal de voz donde está el bot, y necesita el intent privilegiado de
> contenido de mensajes.

## How it works

Text goes to **Piper**, a neural speech engine that runs entirely on your machine: the
model, the phonemizer and ONNX Runtime are all bundled. Nothing is sent to a third party,
which also means it keeps working offline.

The result is a WAV file that is queued into the same player as the music, so speech and
music share one queue and the music ducks while the bot talks.

## Voices

| | |
|---|---|
| Bundled | `es_ES-sharvard-medium` (Spanish, two voices), `en_US-lessac-medium`, `en_US-libritts_r-medium` (English, many voices) |
| Downloadable | Spanish (Mexico), British English, German, and more from the built-in catalog |
| Size | 60–80 MB each |

**Text to speech → More voices** downloads with a progress bar. Every download is verified
by size, and by SHA-256 where the catalog records one, before it is published into your
voices folder — a truncated file can never look installed.

### Adding your own voice

Drop a matching pair into `<dataDir>/voices`:

```
myvoice.onnx
myvoice.onnx.json
```

Press **Text to speech** again (or restart) and it appears in the list. Piper voices are
published at <https://huggingface.co/rhasspy/piper-voices>.

### Multiple speakers

Multi-speaker models expose their speaker table, and the app shows how many voices a model
contains. The default speaker is the first one; the model's own names are listed in the
voice table.

## Speed

Piper expresses speed as `length_scale`, not as a runtime argument, so the app writes a
derived config per (voice, speed) into its cache and loads the voice with that. Adjust the
slider in **Text to speech → Speech settings**; it applies to the next utterance.

## Read-aloud

When enabled, the bot reads messages written in **the text chat of the voice channel it is
in**, and only from members who are in that channel.

This is deliberately narrow: a bot that reads every channel of a server is unbearable, and
this rule is easy to explain. Everything else goes through `/tts say`.

Read-aloud needs the privileged **Message Content Intent**:

1. Open the Developer Portal → your application → **Bot**.
2. Enable **Message Content Intent**.
3. Turn read-aloud on in the app.

BotDealer does not request the intent unless read-aloud is enabled, because asking for a
disabled privileged intent makes the whole login fail.

## Ducking

While the bot speaks, the music volume drops to a percentage of its current level (25 % by
default) and is restored shortly after the clip ends. Turn it off in the speech settings if
you prefer the music untouched.

## Exporting to WAV

**Export WAV** synthesizes the test phrase and writes `export.wav` next to your voices.
Useful for checking a voice without joining a voice channel, or for using the speech
elsewhere.

## When speech is unavailable

If the Piper natives cannot load on your platform, the screen says so and explains why,
and everything else keeps working. The reason is also in the log. Speech needs a
supported architecture (x86-64 or arm64 on Windows, Linux and macOS).

## Licensing

Piper and its phonemizer are GPL-3.0, which is why BotDealer as a whole is GPL-3.0. See
[../THIRD_PARTY_LICENSES.md](../THIRD_PARTY_LICENSES.md).
