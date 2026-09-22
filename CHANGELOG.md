# Changelog

All notable changes to BotDealer are documented here. Format follows Keep a Changelog (Unreleased / versions).

- Phase 3b: betting and settlement.
  - `BettingMath` holds every payout rule as pure functions: pools, rake, parimutuel
    shares and fixed-odds payouts.
  - Parimutuel uses **largest-remainder apportionment**: exact shares are truncated to
    cents and the leftover cents go to the winners with the largest fractional part, so
    payouts sum to exactly the net pool. No money is created or lost to rounding.
  - If nobody backed the winning option every stake is refunded instead of the pot
    vanishing; fixed odds are paid at the multiplier frozen when each bet was placed and
    carry no extra rake (the margin is already in the odds).
  - `BetService.place` validates and debits and writes the bet in one transaction, so a
    rejected bet never moves money and a failed write never leaves a paid-for bet.
  - `EventService` owns the lifecycle (create/open/close/auto-close/cancel/settle);
    settlement credits every winner, marks every loser and flips the event in one
    transaction.
  - `EventQueryService` builds the live view (pools, bet counts, implied odds, fixed-odds
    liability warnings).
  - Fixed a design bug the tests caught: the house wallet was created with the member
    starting balance, which would have minted money when collecting the rake. It now
    starts at zero and only ever holds rake.
  - 116 tests green.

### Added
- Phase 3a: gambling domain model and economy.
  - Entities: `GuildConfig` (per-guild overrides), `Wallet` (`@Version` optimistic
    locking, unique per guild+user), `LedgerEntry` (append-only audit with the balance
    after each movement), `BetEvent`, `BetOption`, `Bet`, plus the `PayoutMode`,
    `EventStatus`, `BetStatus` and `LedgerReason` enums.
  - `GuildConfigService` is the single place where per-guild overrides are merged with
    the application defaults, so callers never care where a value came from.
  - `WalletService` is the only way money moves: every mutation runs in a
    `TransactionTemplate` so it can be retried as a whole on an optimistic-lock
    conflict, and always writes its ledger entry in the same transaction.
  - `DailyRewardService` delegates to an atomic claim that re-checks the cooldown inside
    the transaction, so two simultaneous claims cannot both be paid.
  - `Money` centralises rounding: user input half-up, payouts truncated down so
    rounding can never create money.
  - 75 tests green, including a concurrent daily-claim test that asserts exactly one of
    eight simultaneous claims is paid.

### Added
- Phase 2d: GitHub Actions pipelines.
  - `build.yml` runs the unit tests on every push/PR and additionally packages a Linux
    application image and runs `--self-test` on it, so packaging regressions fail fast.
  - `release.yml` triggers on `v*` tags (and `workflow_dispatch` for dry runs): it
    aligns the project version with the tag, builds `.rpm` + `.deb` + Linux app image +
    portable jar on Ubuntu and `.exe` + Windows app image on Windows, self-tests both
    images, generates `SHA256SUMS` and publishes the GitHub release.
  - Dry runs never publish a release; they stay as downloadable workflow artifacts.
- Phase 2c: `--self-test` headless verification, run by CI on every packaged build.
  It boots Spring without JavaFX and checks the data folder, the database, the
  translations, the credential backend, the bundled assets and the native libraries
  (JDAVE, Opus, Piper), then exits with a status code. Fatal checks decide the exit
  code; optional capabilities are warnings.
- Phase 2a: native packaging with jpackage.
  - `package` Maven profile producing a self-contained application image plus
    `.rpm`, `.deb`, `.exe` or `app-image` installers (`-Djpackage.type=...`), with the
    app version overridable from the release tag.
  - The image bundles its own jlink runtime and native launcher, so end users install
    nothing. JavaFX rides on the classpath (the main class is deliberately not an
    `Application` subclass).
  - `docs/BUILD.md` documents the packaging flow, required platform tools and the
    jlink module list.
- Phase 1c: application shell and first-run wizard.
  - Sidebar shell (Dashboard, Events, Wallets, Music, TTS, Settings, About) with a
    status bar showing bot/token state, music engine, currency and credential backend.
  - Six-step wizard: language, Discord token (validated live before saving), economy
    (currency name, starting balance, daily reward), music engine choice, TTS voice
    and speed, and a summary. Nothing is persisted until "Finish".
  - Language switch from the sidebar and the settings screen, applied instantly.
  - Settings: masked token display, "forget token", credential backend info, data
    folder shortcut and a guarded full reset that relaunches the app.
  - About screen with the GPL-3.0 notice and third-party credits.
  - Full EN/ES bundles (one default English bundle + `messages_es.properties`) with a
    key-parity test and a guard against malformed keys.
  - Design-system CSS for the wizard, shell, cards and controls in the project palette.
- Phase 1b: settings persistence and reset.
  - `AppSetting` entity + repository, `AppSettingKey` with defaults, typed
    `AppSettingsService` (corruption-proof parsing, snapshot/replaceAll) and
    `ResetService` (wipes every entity found in the JPA metamodel, credentials and
    downloaded assets; relaunches when packaged).
- Phase 1a: credential storage and Discord token validation.
  - `SecretStore` with an OS keychain backend (probed with a canary entry) and an
    AES-256-GCM encrypted-file fallback (PBKDF2-HMAC-SHA256, 210k iterations, 0600).
  - `SecretService` facade with masking and `removeAll`, `TokenPatterns` shared by the
    log redactor and the UI masker, and `TokenValidator` checking `/users/@me` before
    anything is stored.
  - `JacksonConfig` (explicit `ObjectMapper`; Spring Boot 4 only auto-configures
    Jackson when a JSON starter is present).
- Phase 0 foundation.
- Build: Spring Boot 4.0.6 on Java 25, JavaFX 25.0.4, JDA 6.7.0, JDAVE 0.1.8 (+ all platform natives),
  udpqueue 0.2.12, opus-java 1.1.1, LavaPlayer 2.2.7, youtube-source v2 1.18.2, piper-jni 1.4.1,
  java-keyring 1.0.4, H2, Lombok 1.18.48. Repositories: Maven Central, maven.lavalink.dev, JitPack.
- Correct Spring + JavaFX bootstrap: plain main class, Spring context in `Application#init()`,
  `StageReadyEvent`, FXML loader with Spring controller factory (`FxViewLoader`).
- Desktop app without web server: no actuator, no H2 console.
- Bilingual UI base (English/Spanish): OS language detection, runtime switching, `I18nService`.
- Per-user data directory (`%APPDATA%/BotDealer`, `~/.local/share/BotDealer`) via `AppPaths`;
  H2 file DB and rotating logs live there. No more `./db` relative to the working directory.
- Token-safe logging: `RedactingMessageConverter` (`%safeMsg`) and `RedactingThrowableConverter` (`%safeEx`) redact
  Discord-style secrets in messages and stack traces on every appender.
- Design system: `base.css` / `components.css` / `effects.css` in the original palette
  (green `#80ed99`, purple `#301466`, teal `#38a3a5`).
- CI test job (`.github/workflows/build.yml`): build + unit tests on every push/PR.
- Docs skeleton: bilingual README, `docs/ARCHITECTURE.md`, `THIRD_PARTY_LICENSES.md`.

### Changed
- Package renamed `souris.jarvisdealer` -> `souris.botdealer`; artifact `jarvisdealer` -> `botdealer`.
- Project license MIT -> **GPL-3.0** (required by the bundled Piper/espeak-ng natives).
- Removed `spring-boot-starter-actuator`, `spring-boot-h2console`, hardcoded H2 password,
  and the broken `SpringApplication.run` + `Application.launch` ordering.

### Fixed
- JitPack was searched before Maven Central and answered for the GitHub-style group
  `io.github.jvoice-project`, shadowing the real 76 MB `piper-jni` jar with a 15 KB stub
  that contains no native libraries. Offline TTS would have been dead on arrival.
  Maven Central is now declared first, so JitPack only serves what Central lacks.
- Log files had no line breaks: the composite converter form `%mask(%msg%ex)` swallowed
  the rest of the Logback pattern, so `%n` was emitted literally. Replaced with plain
  `%safeMsg`/`%safeEx` classic converters, which also redact stack traces.
- The packaged app silently lost OS keychain support: java-keyring's freedesktop backend
  needs `com.sun.security.auth.module.UnixSystem` from the `jdk.security.auth` module,
  which is not part of `java.se`. The module is now part of the jlink runtime.
- The keychain probe is bounded by a 5 second timeout and runs on a daemon virtual
  thread, so a locked or prompting keyring can no longer hang application startup.

### Removed
- Old `MainApp` launcher, the placeholder boot screen (`start.fxml`/`StartController`) and
  `start.fx.css` (replaced by the wizard and the design system).
