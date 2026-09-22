# Changelog

All notable changes to BotDealer are documented here. Format follows Keep a Changelog (Unreleased / versions).

## [Unreleased]

### Added
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
- Token-safe logging: `TokenMaskingConverter` (`%mask`) redacts Discord-style secrets in every appender.
- Design system: `base.css` / `components.css` / `effects.css` in the original palette
  (green `#80ed99`, purple `#301466`, teal `#38a3a5`).
- CI test job (`.github/workflows/build.yml`): build + unit tests on every push/PR.
- Docs skeleton: bilingual README, `docs/ARCHITECTURE.md`, `THIRD_PARTY_LICENSES.md`.

### Changed
- Package renamed `souris.jarvisdealer` -> `souris.botdealer`; artifact `jarvisdealer` -> `botdealer`.
- Project license MIT -> **GPL-3.0** (required by the bundled Piper/espeak-ng natives).
- Removed `spring-boot-starter-actuator`, `spring-boot-h2console`, hardcoded H2 password,
  and the broken `SpringApplication.run` + `Application.launch` ordering.

### Removed
- Old `MainApp` launcher, the placeholder boot screen (`start.fxml`/`StartController`) and
  `start.fx.css` (replaced by the wizard and the design system).
