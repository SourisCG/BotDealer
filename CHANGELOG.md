# Changelog

All notable changes to BotDealer are documented here.
Format follows Keep a Changelog; versions follow semantic versioning.

## [0.1.0] - 2026-09-22

First complete version: every planned phase is implemented, tested and verified.

### Added

- Phase 6: documentation and polish.
  - Bilingual guides: INSTALL, USER_GUIDE, DISCORD_SETUP, MUSIC, TTS, TROUBLESHOOTING,
    FAQ, SECURITY, ARCHITECTURE, BUILD and CONTRIBUTING.
  - Optional per-user configuration at `<dataDir>/config/application.properties`, so
    settings can be overridden without touching the read-only app image.

- Phase 5d: Text to speech screen.
  - Installed voices with language, speaker count and size: set as default, preview
    through Discord, export to WAV (handy without a voice channel) and delete.
  - Download library listing the voices that are not installed, with a size hint, a
    progress bar and verification feedback.
  - Speech settings: speed, read-aloud and music ducking, plus an engine card that
    explains why speech is unavailable instead of leaving dead buttons.

- Phase 5b: downloadable voice library.
  - `VoiceCatalog` ships a curated list inside the app, so the URLs are fixed and
    reviewable and the screen works offline. Three voices ship in the installer.
  - `VoiceDownloader` writes to temporary files and only publishes a voice once the size
    and (when recorded) the SHA-256 of both files match, so a truncated or tampered
    download can never look installed. Downloads can be cancelled.

- Phase 5c: speech playback and commands.
  - `TtsService` synthesizes on a background thread and queues the result into the
    guild's music player, ducking the music and restoring the volume after the clip.
  - `ReadAloudListener` reads messages from the voice channel's own text chat, only from
    members in that channel. Everything else goes through `/tts say`.
  - `/tts say|join|leave|voice|on|off|status`.

- Phase 5a: offline speech foundation.
  - `PiperTtsEngine` runs Piper locally: every call is serialized on a single worker
    thread (Piper is not thread-safe) and the caller blocks with a timeout. Speed is
    Piper's `length_scale`, so a derived config is written per (voice, speed) and cached.
  - `VoiceRegistry` discovers voices by pairing `.onnx` with `.onnx.json` and reading
    sample rate, language and speaker table from the metadata; a malformed voice is
    skipped rather than fatal.
  - `TtsTextSanitizer` strips mentions, custom emoji, links and markdown, collapses
    whitespace and truncates on a word boundary.
  - `WavWriter` produces the 16-bit mono WAV that LavaPlayer plays natively.

- Phase 4d: Music screen and YouTube account linking.
  - Voice-channel connect/disconnect, now-playing card with artwork and a progress bar,
    transport controls, volume, repeat, shuffle, the numbered queue, and a search box
    that plays through the same engine and queue as the Discord commands.
  - Engine card with versions, a verified in-place update and a shortcut to the folder.
  - YouTube account linking using Google's official device-code flow, with the refresh
    token stored in the OS keychain.

- Phase 4c: music slash commands.
  - `/play`, `/pause`, `/resume`, `/skip`, `/stop`, `/queue`, `/nowplaying`, `/volume`,
    `/loop`, `/shuffle`, `/disconnect`, one root command per action.
  - Control commands require being in the bot's voice channel, the DJ role or Manage
    Server; `/play` only requires being in a voice channel.
  - `/play` defers and `MusicAnnouncer` reports the outcome in the originating channel.

- Phase 4b: playback.
  - `DirectLoaderImpl` wires LavaPlayer with Http (yt-dlp stream URLs), local files and
    the maintained YouTube source with thumbnail clients. LavaPlayer's own **deprecated**
    YouTube source is not registered, so it cannot shadow the live one.
  - `TrackScheduler` owns the bounded queue, repeat OFF/TRACK/QUEUE and shuffle. It
    advances on FINISHED and LOAD_FAILED and repeats only a track that actually finished.
  - `LavaplayerSendHandler` passes Opus frames straight to Discord without re-encoding.
  - `MusicService` joins the caller's voice channel, resolves asynchronously, enforces
    track-length and queue caps, and exposes the transport controls plus a DJ check.

- Phase 4a: music engine layer.
  - `EngineRouter` implements YOUTUBE_DIRECT, YTDLP and AUTO (in-process, then yt-dlp,
    then a cached download when the resolved stream will not play).
  - `YtDlpTargets` vets user input before it becomes an argument: plain text becomes a
    `ytsearch1:` query, URLs are limited to YouTube hosts, and `file://`, other schemes,
    flag-like input, control characters and paths are rejected.
  - `YtDlpCommandBuilder` builds argument lists (never shell strings), always places `--`
    before the target and passes `--ignore-config`.
  - `ProcessRunner` bounds every invocation with a timeout, caps output and force-kills.
  - `EngineInstaller` seeds bundled engines into the writable data folder and never
    overwrites an already updated binary.
  - `MediaCache` caps downloaded audio and prunes least-recently-used files first.
  - `EngineUpdater` refreshes yt-dlp and Deno: temp download, SHA-256 verified against the
    checksum published by the same release, previous binary kept until the new one answers
    `--version`, rollback if it does not.

- Phase 3d: desktop screens for the gambling features.
  - Dashboard with live bot status, server count, money in circulation and open events.
  - Events screen: table, detail panel with per-option pools and implied odds, and
    Open/Close/Settle/Cancel with confirmations. The editor dialog has dynamic option
    rows, a payout-mode toggle, a closing time, rake, and validates with the same service
    rules before closing.
  - Wallets screen: searchable balances, grant/remove/set, and the full ledger of the
    selected member.
  - Servers screen: per-guild overrides where an empty field means "use the app default".

- Phase 3c: Discord bot and slash commands.
  - `DiscordBotService` owns the JDA session. The token is read from the keychain at
    connect time, never kept in a field and never logged. Voice is configured with JDAVE
    and the native send factory; if those natives cannot load the bot still connects with
    voice disabled.
  - `MESSAGE_CONTENT` is requested only when read-aloud TTS is enabled, because asking for
    a disabled privileged intent makes the whole login fail.
  - Commands register per guild on GuildReady, so they appear immediately.
  - `/botdealer`, `/chorizos`, `/bet`, `/event`, `/economy`, all replies localized per
    guild so a server can run in Spanish while the operator's desktop stays in English.
  - The live event card is posted and refreshed as bets arrive, and results are announced.

- Phase 3b: betting rules and settlement.
  - `BettingMath` holds every payout rule as pure functions.
  - Parimutuel uses largest-remainder apportionment, so payouts sum to exactly the net
    pool. When nobody backed the winning option every stake is refunded.
  - Fixed odds are paid at the multiplier frozen when each bet was placed and carry no
    extra rake.
  - `BetService.place` validates, debits and writes the bet in one transaction.
  - `EventService` owns the lifecycle, and settlement credits every winner and marks every
    loser atomically.

- Phase 3a: gambling domain model and economy.
  - Entities: `GuildConfig`, `Wallet` (optimistic locking), `LedgerEntry` (append-only
    audit), `BetEvent`, `BetOption`, `Bet`.
  - `GuildConfigService` is the single place that merges guild overrides with app defaults.
  - `WalletService` is the only way money moves: every mutation runs in a transaction that
    can be retried as a whole on a lock conflict, and always writes its ledger entry.
  - `DailyRewardService` delegates to an atomic claim that re-checks the cooldown inside
    the transaction.

- Phase 2b: bundled voices and engines.
  - `AssetFetcher` fills the jpackage input with three Piper voices plus yt-dlp and Deno,
    reusing the application's own download and verification code.
  - `AssetSeeder` copies the bundled content into the writable data folder on first
    launch, because an installed app image is read-only and the engines must be able to
    update themselves.
  - `-Dskip.assets=true` builds a small installer instead.

- Phase 2d: GitHub Actions pipelines.
  - `build.yml` runs the tests on every push/PR and packages a Linux image, running
    `--self-test` on the result.
  - `release.yml` builds `.rpm`, `.deb`, the Linux app image and the portable jar on
    Ubuntu, plus `.exe` and the Windows app image, self-tests both, writes `SHA256SUMS`
    and publishes the release. Dry runs publish nothing.

- Phase 2c: `--self-test`.
  - Boots Spring without JavaFX and checks the data folder, database, translations,
    credential backend, bundled assets and native libraries, then exits with a status code.

- Phase 2a: native packaging with jpackage.
  - `package` profile producing a self-contained application image plus `.rpm`, `.deb`,
    `.exe` or `app-image`, with the version overridable from the release tag.
  - JavaFX is loaded from the classpath instead of the module path, because no JavaFX
    jmods are published to Maven Central.

- Phase 1c: application shell and first-run wizard.
  - Sidebar shell with a status bar showing bot state, music engine, currency and
    credential backend.
  - Six-step wizard: language, Discord token validated live before saving, economy, music
    engine, speech, and a summary. Nothing is persisted until Finish.
  - Settings with masked token, forget token, credential backend info, data folder
    shortcut and a guarded full reset.

- Phase 1b: settings persistence and reset.
  - `AppSetting` entity, `AppSettingKey` with defaults, a corruption-proof
    `AppSettingsService`, and `ResetService` which wipes every entity, the credentials and
    the downloaded assets.

- Phase 1a: credential storage and Discord token validation.
  - `SecretStore` with an OS keychain backend (probed with a canary entry and bounded by a
    timeout) and an AES-256-GCM encrypted-file fallback.
  - `TokenPatterns` shared by the log redactor and the UI masker, and `TokenValidator`
    checking `/users/@me` before anything is stored.

- Phase 0: foundation.
  - Spring Boot 4.0.6 on Java 25, JavaFX 25.0.4, JDA 6.7.0, JDAVE 0.1.8, udpqueue 0.2.12,
    opus-java 1.1.1, LavaPlayer 2.2.7, youtube-source v2 1.18.2, piper-jni 1.4.1,
    java-keyring 1.0.4, H2 and Lombok.
  - Correct Spring + JavaFX bootstrap: a plain main class, the Spring context in
    `Application#init()`, a `StageReadyEvent`, and an FXML loader with a Spring controller
    factory.
  - Per-user data directory (`%APPDATA%\BotDealer`, `~/.local/share/BotDealer`), with the
    H2 database and rotating logs inside it instead of relative to the working directory.
  - Bilingual UI base with OS language detection, runtime switching and key-parity tests.
  - Token-safe logging: messages and stack traces are redacted on every appender.
  - Design system CSS in the original palette (green `#80ed99`, purple `#301466`, teal
    `#38a3a5`).

### Changed

- Package renamed `souris.jarvisdealer` to `souris.botdealer`; artifact `jarvisdealer` to
  `botdealer`; version `0.1.0`.
- Project license MIT to **GPL-3.0**, required by the bundled Piper/espeak-ng natives.
- yt-dlp's JavaScript challenge solver is enabled (`--remote-components ejs:github`)
  instead of blocked: since late 2025 most YouTube formats need it, and blocking it left
  the reinforced engine degraded. Documented in `docs/SECURITY.md`.
- Removed `spring-boot-starter-actuator`, `spring-boot-h2console`, the hardcoded H2
  password, and the broken `SpringApplication.run` + `Application.launch` ordering.

### Fixed

- **JitPack was searched before Maven Central** and answered for the GitHub-style group
  `io.github.jvoice-project`, shadowing the real 76 MB `piper-jni` jar with a 15 KB stub
  that contains no native libraries. Offline TTS would have been dead on arrival.
- **The packaged app silently lost OS keychain support**: java-keyring's freedesktop
  backend needs `com.sun.security.auth.module.UnixSystem` from the `jdk.security.auth`
  module, which is not part of `java.se`. It is now in the jlink runtime.
- **Log files had no line breaks**: the composite converter form `%mask(%msg%ex)`
  swallowed the rest of the Logback pattern, so `%n` was written literally. Replaced with
  plain `%safeMsg`/`%safeEx` converters, which also redact stack traces.
- **The house wallet was minting money**: it was created through the normal path and
  therefore received the member starting balance, so collecting the rake would have
  created currency from nothing.
- **The daily reward could be paid twice**: the credit and the cooldown stamp were in
  separate transactions. It is now one atomic claim that re-checks the cooldown inside the
  transaction.
- **`application.properties` was gitignored**, so CI-built jars would have used an
  in-memory database and lost all data on restart.
- **Table `user` is a reserved word in H2**, which broke schema generation. The real
  schema uses explicit, non-reserved column names.
- **A circular dependency** appeared twice, once through the announcers and once through
  the music chain; the bot now resolves its dispatcher lazily at connect time.
- **FXML wiring bugs** that only appear when a screen is opened: removed i18n keys still
  referenced by FXML, a missing `#speak` handler, and stale `fx:id`s. Three new tests now
  scan FXML and Java sources for missing translation keys and unmatched ids and handlers.
- **The i18n parity test was lying**: `ResourceBundle` falls back to the default bundle, so
  `keySet()` on the Spanish bundle reported the English keys and missing translations were
  invisible. The test now compares the two `.properties` files directly.
- **Repeat-queue silently broke the loop** whenever the queue happened to be full, and
  `stopTrack` does not advance the queue (`STOPPED.mayStartNext` is false), which a test
  now documents.

### Removed

- The old `MainApp` launcher, the placeholder boot screen and `start.fx.css`, replaced by
  the wizard and the design system.
