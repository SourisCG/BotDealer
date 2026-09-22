# BotDealer

> 🇬🇧 **BotDealer** is a Discord bot for play-money betting, YouTube music and offline
> text-to-speech, driven from Discord **and** from a JavaFX desktop app at the same time.
> Everything ships in one installer: no Java to install, no Python, no external server.
>
> 🇪🇸 **BotDealer** es un bot de Discord para apostar dinero ficticio, poner música de
> YouTube y leer texto en voz alta sin conexión, controlado desde Discord **y** desde una
> app de escritorio JavaFX al mismo tiempo. Todo viene en un solo instalador: no hay que
> instalar Java, ni Python, ni ningún servidor externo.

**License: GPL-3.0** · Third-party components: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) · Security model: [docs/SECURITY.md](docs/SECURITY.md)

---

## What it does / Qué hace

| | |
|---|---|
| 🎲 **Betting** | Create events with two or more options, shared pot or fixed odds. Members bet *chorizos* (rename them to anything), the app settles the winner and pays out. Every movement is recorded in an auditable ledger. |
| 🎵 **Music** | YouTube links, searches and playlists plus local files, into a voice channel, with queue, skip, loop, shuffle and volume. Two engines: in-process and a self-updating yt-dlp fallback. |
| 🗣️ **Text to speech** | Offline neural voices (Piper) that run on your own machine. Read a phrase, read the voice channel chat aloud, or export to WAV. Music ducks while the bot speaks. |
| 🖥️ **Desktop app** | Dashboard, events, wallets, servers, music, speech, settings. Bilingual (Spanish/English), detects your system language, switchable at any time. |

## Install / Instalar

Download the file for your system from [Releases](https://github.com/SourisCG/BotDealer/releases):

| System | File |
|---|---|
| Windows | `BotDealer-<version>.exe` |
| Fedora / RHEL | `BotDealer-<version>.rpm` |
| Debian / Mint / Ubuntu | `BotDealer-<version>.deb` |
| Any, no installation | `BotDealer-<version>-linux-app-image.tar.gz` or `-windows-app-image.zip` |
| Any, if you already have Java 25 | `botdealer-<version>.jar` |

Installers bundle their own Java runtime, so nothing else is required. Details and
uninstall instructions: [docs/INSTALL.md](docs/INSTALL.md).

## First run / Primer inicio

The app opens a six-step wizard:

1. **Language** — detected from your system, changeable later.
2. **Discord token** — paste it and press *Test connection*; nothing is saved until
   Discord confirms it. The token goes into your OS keychain, never into a file in the
   project and never into the database.
3. **Economy** — what the money is called, starting balance and daily reward.
4. **Music engine** — Automatic (recommended), Direct or Reinforced.
5. **Speech** — pick a bundled voice and a speed.
6. **Summary** — review and start.

Full walkthrough: [docs/USER_GUIDE.md](docs/USER_GUIDE.md). Discord setup (creating the
bot, intents, inviting it): [docs/DISCORD_SETUP.md](docs/DISCORD_SETUP.md).

## Build from source / Compilar

Requirements: **JDK 25** and Maven 3.9+.

```bash
mvn verify        # compile + 215 tests
mvn javafx:run    # run the desktop app
```

Packaging (self-contained installers, ~650 MB because the voices and engines are
bundled):

```bash
mvn -Ppackage -Djpackage.type=rpm package        # Fedora / RHEL
mvn -Ppackage -Djpackage.type=deb package        # Debian / Mint / Ubuntu
mvn -Ppackage -Djpackage.type=exe package        # Windows
mvn -Ppackage -Dskip.assets=true package         # small installer, downloads on demand
```

See [docs/BUILD.md](docs/BUILD.md).

## Documentation / Documentación

| | |
|---|---|
| [docs/INSTALL.md](docs/INSTALL.md) | Install, update, uninstall, portable builds |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | Every screen and command |
| [docs/DISCORD_SETUP.md](docs/DISCORD_SETUP.md) | Creating the bot, intents, inviting it |
| [docs/MUSIC.md](docs/MUSIC.md) | Engines, YouTube account linking, limits |
| [docs/TTS.md](docs/TTS.md) | Voices, read-aloud, adding your own |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | When something does not work |
| [docs/FAQ.md](docs/FAQ.md) | Short answers |
| [docs/SECURITY.md](docs/SECURITY.md) | Where secrets live, what the app does on the network |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | How it is put together |
| [docs/BUILD.md](docs/BUILD.md) | Building, packaging, releasing |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | Working on the code |

## Status / Estado

All six planned phases are implemented. 215 tests run in CI on every push, and every
packaged build is verified headlessly by `BotDealer --self-test`, which checks the
database, translations, credential store, bundled assets and native libraries.

```bash
./BotDealer --self-test    # from an installed app image
```

### Known limitations / Limitaciones conocidas

- YouTube playback depends on YouTube: if it changes something, the reinforced engine can
  update itself from the app, the direct one needs a new BotDealer release.
- Windows installers are not code-signed, so SmartScreen shows a warning the first time.
- Offline speech needs the Piper natives; on an unsupported architecture the app says so
  and everything else keeps working.
- The portable `.jar` needs Java 25 installed; the installers do not.
