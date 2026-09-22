# Music

> 🇪🇸 **Resumen:** BotDealer tiene dos motores. El **directo** reproduce dentro de la app,
> sin componentes extra. El **reforzado** usa yt-dlp y Deno (ya incluidos) y se actualiza
> solo cuando YouTube cambia. El modo **Automático** intenta el directo y cae al reforzado.
> Vincular una cuenta de YouTube es opcional y ayuda cuando YouTube pide verificar que no
> sos un bot.

## The three modes

| Mode | Behaviour |
|---|---|
| **Automatic** (default) | Tries the in-process engine, falls back to yt-dlp on failure, and if the resolved stream will not play it downloads the audio and plays the file. |
| **Direct** | In-process only. No external components at all. If YouTube changes something, it waits for a BotDealer release. |
| **Reinforced** | yt-dlp only. Most resilient, and it can be updated from the app without a new release. |

Change it in **Settings → Music engine** or in the setup wizard.

## What is bundled

The installer ships **yt-dlp** and **Deno** (the JavaScript runtime yt-dlp needs since late
2025), plus three Piper voices. They are copied into `<dataDir>/engines` on first launch,
because an installed app image is read-only and the engines must be able to update
themselves.

**Music → Update engines** downloads the latest yt-dlp and Deno, verifies their SHA-256
against the checksum published by the same release, and only then replaces the working
binary — keeping the previous one until the new one answers `--version`. A bad download
therefore cannot break your setup.

## YouTube account linking (optional)

YouTube increasingly answers anonymous requests with *"sign in to confirm you're not a
bot"*. Linking a Google account makes the requests look like a normal signed-in user and
removes the need for a PO token.

1. **Music → YouTube account → Link account**.
2. The dialog shows a page and a code. Open the page, sign in and type the code.
3. The dialog polls until Google confirms, then stores the refresh token in your OS
   keychain.

Notes:

- Use a **throwaway account**. The library itself warns about this, and the dialog repeats
  it.
- It is the official Google device flow: BotDealer never sees your password.
- **Unlink** removes the token; requests go back to being anonymous.

## Queue and limits

- The queue is bounded (default 100 tracks) and tracks longer than 30 minutes are refused.
  Both are configurable per server or globally.
- `/queue` numbers the entries; the Music screen numbers them the same way, so removing
  entry 3 is unambiguous.
- `/loop` cycles off → track → queue. Repeating a *track* only happens when it actually
  finished; a track that failed to load is skipped instead of looping the failure.

## Local files

Put audio files in the folder shown under **Music → Local files** (default
`<dataDir>/music`). They play through the same queue, so they can be mixed with YouTube
tracks.

## Why the bot does not play ads

There is no YouTube player involved, so there are no player ads. YouTube's server-side ad
insertion is a different matter: if YouTube stitches an ad into the stream for the client
being used, no tool can skip it.

Worth knowing: **creators earn nothing from this playback.** If you like a track, give it a
real play too.

## When it breaks

YouTube changes its internals regularly. In order:

1. **Music → Update engines** — fixes most cases without touching BotDealer.
2. **Link a YouTube account** — fixes "prove you're not a bot".
3. **Wait for a BotDealer release** — needed when the in-process engine itself must be
   updated.

The app checks the releases page so you know when one is available.
