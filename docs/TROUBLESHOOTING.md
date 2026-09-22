# Troubleshooting

> 🇪🇸 **Resumen:** casi todo se arregla mirando el estado en la barra superior y los
> registros en la carpeta de datos. Abajo están los problemas más comunes con su causa y su
> solución.

First stop: the app's **status bar** and `<dataDir>/logs/botdealer.log`. The log has
token-shaped text redacted, so it is safe to share.

## The bot will not connect

| What you see | Cause | Fix |
|---|---|---|
| `No Discord token is configured` | Nothing stored yet. | Paste it in **Settings**, press *Test connection*. |
| `Discord rejected the token` | Token is incomplete, or was regenerated. | Copy the whole value again from the Developer Portal. |
| `Message Content intent is not enabled` | Read-aloud is on but the intent is off. | Enable **Message Content Intent** in the Developer Portal, or turn read-aloud off. |
| `Could not reach Discord` | Network, firewall or proxy. | Check the connection; a proxy can be set in `config/application.properties`. |

## Commands do not appear in Discord

Commands are registered per guild when the bot sees the server. If they are missing:

1. Make sure the bot is **Connected** in the app.
2. Kick the bot and invite it again — Discord caches command lists per client.
3. Restart your Discord client (Ctrl+R).

## No sound in the voice channel

1. Is the bot actually in the channel? The **Music** screen shows the connection state.
2. Does the bot have **Connect** and **Speak** permissions in that channel?
3. Check the log for `Discord voice unavailable`. If the DAVE encryption natives cannot
   load on your platform, the bot connects but cannot play audio; the app says so in the
   log and everything else keeps working.
4. Try `/volume 100`.

## A track will not play

| Message | Meaning |
|---|---|
| `I could not find anything for that` | The search found nothing, or the link is not a video. |
| `That could not be played` | The engine failed. Open **Music → Update engines** and try again. |
| `Tracks are limited to N minutes` | The track exceeds the server limit. |
| `The queue is full` | Raise the limit under **Servers**, or clear the queue. |

YouTube changes its internals regularly. The reinforced engine (yt-dlp) can be updated
from the app; the direct engine needs a newer BotDealer release.

## "Sign in to confirm you're not a bot"

YouTube sometimes wants proof that the client is human. Open **Music → YouTube account →
Link account** and follow the device-code flow. The library recommends a throwaway account,
and the dialog says so. The refresh token goes into your OS keychain.

## Speech does nothing

| What you see | Cause | Fix |
|---|---|---|
| `Offline speech is unavailable` | The Piper natives did not load. | Check the log; on an unsupported architecture speech stays off and everything else works. |
| `No voice is selected` | No voice installed. | **Text to speech → More voices** and download one. |
| `The bot is not in a voice channel` | Speech shares the music connection. | Connect the bot to a channel first. |
| Read-aloud never triggers | The intent is off, or the message was in another channel. | See the read-aloud note in [DISCORD_SETUP.md](DISCORD_SETUP.md); only the voice channel's own chat is read. |

## The download of a voice fails

Voices are 60–80 MB from `huggingface.co`. A failure usually means a proxy, a firewall or a
temporary network problem. Nothing partial is ever installed, so just try again. If your
network blocks it, voices can be copied manually into `<dataDir>/voices` as a matching
`.onnx` and `.onnx.json` pair.

## The keychain is not used

If Settings shows `Encrypted file` instead of `OS keychain`, the probe failed. The log
says why: on Linux it is usually a locked or absent keyring daemon. The encrypted file is
perfectly usable; to switch to the keychain, unlock it (or install `gnome-keyring`) and
restart BotDealer.

## The app does not start

1. Run `BotDealer --self-test` in a terminal. It checks the database, translations,
   credential store, bundled assets and native libraries, and prints what is wrong.
2. If the window never appears, check `<dataDir>/logs/botdealer.log`.
3. As a last resort, **Settings → Reset application** (or delete `<dataDir>` by hand)
   returns the app to its first-run state.

## Reporting a bug

Open an issue at <https://github.com/SourisCG/BotDealer/issues> with the self-test output
and the relevant log lines. **Never paste a real token**: the log already masks them, and
you should not add one.
