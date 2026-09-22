# BotDealer

> 🇬🇧 Discord gambling bot (play-money *chorizos*), YouTube music and offline TTS, driven from Discord and from a JavaFX desktop app.
> 🇪🇸 Bot de Discord de apuestas (dinero ficticio *chorizos*), música de YouTube y TTS sin conexión, controlado desde Discord y desde una app de escritorio JavaFX.

**License: GPL-3.0** (see `LICENSE`). Third-party licenses: `THIRD_PARTY_LICENSES.md`.

## Status / Estado

Phase 0 foundation (see `docs/ARCHITECTURE.md` and `CHANGELOG.md`).

| Phase | Scope | Status |
|---|---|---|
| 0 | Foundation: build, Spring+JavaFX bootstrap, i18n, design system, security logging | done |
| 1 | First-run wizard, token security, reset | next |
| 2 | jpackage installers (.exe/.rpm/.deb/app-image) + release workflow | planned |
| 3 | Gambling core (events, wallets, ledger, both payout modes) | planned |
| 4 | Music (Direct + yt-dlp engines, Discord voice) | planned |
| 5 | Offline TTS (Piper, voices, read-aloud) | planned |
| 6 | Polish, full docs, hardening | planned |

## Quick start / Inicio rápido

Requirements: **Java 25** and Maven 3.9+.

```bash
mvn verify        # build + tests
mvn javafx:run    # launch the desktop app
```

Configuration lives in `src/main/resources/application.properties` (committed, no secrets).
The Discord token is **never** stored there: it lives in the OS keychain (Phase 1).
La configuración está en `src/main/resources/application.properties` (versionado, sin secretos).
El token de Discord **nunca** se guarda ahí: vive en el llavero del sistema (fase 1).

## Documentation / Documentación

`docs/` holds the bilingual guides (`ARCHITECTURE.md` first). More guides land with each phase.
