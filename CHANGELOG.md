# Changelog

All notable changes to BotDealer are documented here. Format follows Keep a Changelog (Unreleased / versions).

## [Unreleased]

### Added
- Phase 0 foundation.
- Build: Spring Boot 4.0.6 on Java 25, JavaFX 25.0.4, JDA 6.7.0, JDAVE 0.1.8 (+ all platform natives),
  udpqueue 0.2.12, opus-java 1.1.1, LavaPlayer 2.2.7, youtube-source v2 1.18.2, piper-jni 1.4.1,
  java-keyring 1.0.4, H2, Lombok 1.18.48. Repositories: Maven Central, maven.lavalink.dev, JitPack.
- Correct Spring + JavaFX bootstrap: plain main class, Spring context in `Application#init()`,
  `StageReadyEvent`, FXML loader with Spring controller factory (`FxViewLoader`).
- Desktop app without web server: no actuator, no H2 console.
- Bilingual UI base (English/Spanish): OS language detection, runtime switching, `I18nService`,
  `messages.properties` / `messages_en.properties` / `messages_es.properties`, EN/ES key-parity test.
- Per-user data directory (`%APPDATA%/BotDealer`, `~/.local/share/BotDealer`) via `AppPaths`;
  H2 file DB and rotating logs live there. No more `./db` relative to the working directory.
- Token-safe logging: `TokenMaskingConverter` (`%mask`) redacts Discord-style secrets in every appender.
- Design system: `base.css` / `components.css` / `effects.css` in the original palette
  (green `#80ed99`, purple `#301466`, teal `#38a3a5`).
- Boot screen wired to Spring + i18n (placeholder until the Phase 1 setup wizard).
- CI test job (`.github/workflows/build.yml`): build + unit tests on every push/PR.
- Docs skeleton: bilingual README, `docs/ARCHITECTURE.md`, `THIRD_PARTY_LICENSES.md`.

### Changed
- Package renamed `souris.jarvisdealer` -> `souris.botdealer`; artifact `jarvisdealer` -> `botdealer`.
- Project license MIT -> **GPL-3.0** (required by the bundled Piper/espeak-ng natives).
- Removed `spring-boot-starter-actuator`, `spring-boot-h2console`, hardcoded H2 password,
  and the broken `SpringApplication.run` + `Application.launch` ordering.

### Removed
- Old `MainApp` launcher and placeholder `start.fx.css` (replaced by the design system).
