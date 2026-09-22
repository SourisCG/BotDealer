# Architecture

> 🇪🇸 **Resumen:** una sola aplicación Java 25 que arranca Spring y JavaFX juntos. Los
> servicios tienen las reglas y se prueban sin Spring ni Discord; las pantallas y los
> comandos solo traducen. Los secretos viven en el llavero del sistema, nunca en la base de
> datos.

## Runtime shape

BotDealer is a **non-modular** (classpath) Java 25 application combining:

- **Spring Boot 4** for configuration, persistence (H2 + JPA) and services. No web server
  (`spring.main.web-application-type=none`), no actuator, no H2 console.
- **JavaFX 25** for the desktop UI. JavaFX lives on the **classpath**; the main class
  deliberately does **not** extend `javafx.application.Application`, which is what makes
  both the fat jar and the jpackage image start correctly.
- **JDA 6** for Discord, with **JDAVE** providing the DAVE end-to-end encryption that
  Discord has required for voice since March 2026. JDAVE uses the FFM API, which is why
  the whole project requires Java 25 and why packaged builds pass
  `--enable-native-access=ALL-UNNAMED`.

## Startup sequence

```
BotDealerApplication.main()
  -> --self-test?        run diagnostics headlessly and exit
  -> AppPaths.init()     resolves %APPDATA%/BotDealer or ~/.local/share/BotDealer,
                         sets botdealer.data.dir, creates db/logs/config
  -> Application.launch(FxApplication)
       -> FxApplication.init()    SpringApplicationBuilder(...).web(NONE).run(args)
       -> FxApplication.start()   publishes StageReadyEvent
       -> StageInitializer        UiRouter shows the wizard or the shell
       -> ApplicationReadyEvent   AssetSeeder copies bundled engines/voices into the
                                  writable data folder on a background thread
       -> FxApplication.stop()    closes the Spring context
```

`FxApplication` also points Spring at an optional `<dataDir>/config/application.properties`,
so users can override settings without touching the read-only app image.

## Layers

| Package | Responsibility |
|---|---|
| `config` | `AppPaths` (data folder), `BundledAssets` (read-only app image + seeding), `AssetSeeder`, `JacksonConfig`, `SchedulerConfig` |
| `security` | `SecretStore` (keychain / AES-GCM file), `SecretService`, `TokenValidator`, `TokenPatterns` |
| `i18n` | `I18nService`: OS detection, runtime switching, per-guild lookups |
| `domain` / `repository` | JPA entities and Spring Data repositories |
| `service.economy` | `WalletService` (the only way money moves), `LedgerService`, `DailyRewardService`, `GuildConfigService` |
| `service.betting` | `BettingMath` (pure payout arithmetic), `EventService`, `BetService`, `EventQueryService`, `BettingScheduler` |
| `service.music` | `DirectLoaderImpl` (LavaPlayer), `TrackScheduler`, `MusicService`, engine layer (`EngineRouter`, `YtDlpResolver`, `EngineInstaller`, `EngineUpdater`, `YouTubeOauthService`) |
| `service.tts` | `PiperTtsEngine`, `VoiceRegistry`, `VoiceCatalog`, `VoiceDownloader`, `TtsTextSanitizer`, `TtsService` |
| `service.discord` | `DiscordBotService`, `CommandRegistry`/`CommandDispatcher`, commands, embeds, announcers, `ReadAloudListener`, `UiEventBus` |
| `ui` | `UiRouter`, screens, dialogs, `UiSelectionModel` |
| `diagnostics` | `SelfTest` (`--self-test`), `AssetFetcher` (build-time asset download) |

**The rule that keeps this testable:** services own the rules, and UI controllers and
Discord handlers only translate. 215 tests run without a Discord connection, a display or
the network.

## Threading contract

- **JavaFX Application Thread**: UI only.
- **JDA threads**: Discord only. They never touch JavaFX objects; everything that reaches
  the UI goes through `UiEventBus`, which marshals to the FX thread with `Platform.runLater`
  (and falls back to calling listeners inline when the toolkit is not running, so
  `--self-test` works).
- **Virtual threads**: token validation, engine updates, voice downloads, synthesis.
- **LavaPlayer's loader pool**: track resolution, including the blocking yt-dlp fallback.
- **One daemon scheduler**: closing expired events, restoring the music volume after speech.

## Money and bets

`WalletService` is the only component that changes a balance. Every mutation runs inside a
`TransactionTemplate` so `Retry.onConflict` can retry the whole transaction (a
`@Transactional` method could not be retried safely: it is already rollback-only), and each
one writes its ledger entry in the same transaction.

`BettingMath` is pure: pools, rake, parimutuel shares and fixed-odds payouts. Parimutuel
uses **largest-remainder apportionment**, so payouts sum to exactly the net pool — no money
is created or lost to rounding. When nobody backed the winning option, every stake is
refunded.

## Music

`EngineRouter` implements the three modes:

```
YOUTUBE_DIRECT : in-process only (LavaPlayer + youtube-source)
YTDLP          : yt-dlp stream URL, then a cached download
AUTO           : in-process first; on failure yt-dlp; if the stream will not play,
                 download into the cache and play the file
```

LavaPlayer's own **deprecated** YouTube source is deliberately not registered: it still
ships in LavaPlayer 2.2.7 and would shadow the maintained one.

Every yt-dlp invocation is an argument list (never a shell string), always places `--`
before the target, and rejects anything that is not a YouTube URL or a search phrase.

## Speech

Piper runs locally. Synthesis is serialized on one worker thread (Piper is not
thread-safe) and produces a 16-bit mono WAV that LavaPlayer plays natively, so speech
shares the music queue and ducks the music while it plays.

Speed is Piper's `length_scale`, not a runtime argument, so a derived config is written per
(voice, speed) and cached.

## Configuration and secrets

- `application.properties` is committed and contains no secrets; an optional
  `<dataDir>/config/application.properties` overrides it.
- The Discord token and the YouTube refresh token live in the OS keychain, or in an
  AES-256-GCM encrypted file when no keychain is available. They never enter the database.
- Logback redacts token-shaped text in messages and stack traces on every appender.

Details in [SECURITY.md](SECURITY.md).

## Packaging

`mvn -Ppackage` builds the plain classpath jar, copies runtime dependencies into
`target/app`, downloads the bundled voices and engines with `AssetFetcher`, and runs
`jpackage` with a jlink runtime. The result is self-contained: the user installs nothing.

`--self-test` verifies a packaged build headlessly — database, translations, credential
store, bundled assets, native libraries — because a GUI application cannot be launched on a
CI runner. See [BUILD.md](BUILD.md).
