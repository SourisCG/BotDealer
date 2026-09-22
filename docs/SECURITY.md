# Security model

> 🇪🇸 Resumen: el token de Discord nunca se guarda en el proyecto ni en la base de datos:
> vive en el llavero del sistema operativo (o en un archivo cifrado con AES-256-GCM si no
> hay llavero). Los registros se filtran por si acaso, la interfaz solo muestra los
> últimos cuatro caracteres y el bot nunca pide más permisos de los que necesita.

## What BotDealer stores, and where

| Secret | Where it lives | Never |
|---|---|---|
| Discord bot token | OS keychain, or `<dataDir>/secrets/discord_token.enc` (AES-256-GCM) | in the database, in `application.properties`, in logs, in config exports |
| YouTube OAuth refresh token | same credential store | same |

`<dataDir>` is `%APPDATA%\BotDealer` on Windows and `~/.local/share/BotDealer` on Linux.
The application database (H2) holds only game data: wallets, events, bets and settings.

## Token handling rules

1. **Validated before it is stored.** The wizard calls `GET /users/@me` and only writes
   the token to the keychain after Discord confirms it. Nothing is persisted on failure.
2. **Never rendered.** The UI shows `••••` plus the last four characters, only for
   identifying which token is stored. There is no "reveal" control.
3. **Never logged.** `RedactingMessageConverter` (`%safeMsg`) and
   `RedactingThrowableConverter` (`%safeEx`) run on every appender and replace anything
   matching a Discord token pattern, in messages *and* stack traces.
4. **Never in the repository.** `application.properties` is committed but contains no
   secrets, and `.gitignore` keeps local configuration out.

## Credential storage backends

| Backend | Platform | Notes |
|---|---|---|
| Windows Credential Manager | Windows | via java-keyring |
| macOS Keychain | macOS | via java-keyring |
| Freedesktop Secret Service | Linux (GNOME, KDE, …) | via java-keyring; needs a running keyring daemon |
| AES-256-GCM file | fallback | PBKDF2-HMAC-SHA256, 210 000 iterations, random salt and IV per file, atomic writes, `chmod 600`, key bound to the machine and user |

The keychain is probed with a canary entry, bounded by a five second timeout on a daemon
thread: a locked or prompting keyring must never hang startup. If the probe fails,
BotDealer logs the reason and uses the encrypted file, and Settings shows which backend is
active.

**What the encrypted file does and does not protect.** It protects secrets at rest and
keeps them out of backups that do not include the user profile. It is *not* protection
against malware running as the same user, because the key has to be derivable on that
machine.

## Discord permissions

BotDealer asks for the minimum it needs:

- `GUILD_MESSAGES` and `GUILD_VOICE_STATES` always.
- `MESSAGE_CONTENT` **only** when read-aloud text-to-speech is enabled, because it is a
  privileged intent. Requesting it while it is disabled in the Developer Portal makes the
  login fail, so the bot does not ask by default and explains the failure clearly.

Slash commands are registered per guild, and administrative actions require Manage Server,
Administrator, the guild owner, or the admin role configured for that guild. The person
running BotDealer is deliberately **not** special-cased: installing the bot on someone
else's server must not let its operator hand out that server's currency.

## Network activity

| What | When | Notes |
|---|---|---|
| Discord API and gateway | while the bot is connected | token sent only in the `Authorization` header of a TLS request |
| YouTube / googlevideo | while music plays | through LavaPlayer's in-process source manager |
| `huggingface.co` | only when a voice is downloaded | URL comes from the curated catalog inside the app |
| `github.com` | engine updates, and yt-dlp's challenge solver | checksums verified before replacing a binary |
| Google device flow | only when linking a YouTube account | official flow; BotDealer only ever sees the refresh token |

### About yt-dlp's JavaScript solver

Since late 2025 YouTube requires solving a JavaScript challenge (`n`) for most formats.
yt-dlp only fetches its solver when explicitly allowed, so BotDealer passes
`--remote-components ejs:github`. Blocking it would leave the reinforced engine able to
play only the formats that need no challenge.

The component is downloaded from yt-dlp's own release page and executed inside the Deno
runtime that ships with the app, not by the JVM. This is documented rather than hidden
because it is the only case where BotDealer triggers code that was not shipped with it.
Users who prefer not to allow it can choose the **Direct** engine in Settings, which never
invokes yt-dlp.

## Reporting a vulnerability

Open an issue at <https://github.com/SourisCG/BotDealer/issues> describing the problem
without including a real token. If you must show a token shape, use
`YOUR_BOT_TOKEN_HERE` (a fake) instead.
