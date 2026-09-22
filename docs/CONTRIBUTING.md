# Contributing

> 🇪🇸 **Resumen:** el proyecto se compila con Java 25 y Maven. Antes de mandar cambios,
> corré `mvn verify` (215 pruebas) y, si tocás pantallas, abrí cada sección una vez. Los
> commits siguen el estilo del repositorio: mensajes cortos y descriptivos.

## Getting started

```bash
git clone https://github.com/SourisCG/BotDealer.git
cd BotDealer
mvn verify        # compile + tests
mvn javafx:run    # run the app
```

Requirements: **JDK 25** (mandatory: the Discord voice encryption library needs it) and
Maven 3.9+.

## Before you open a pull request

1. `mvn verify` must pass. It runs 215 tests, including guards that are easy to forget:
   - translation key parity between English and Spanish,
   - every `%key` used in FXML existing in both bundles,
   - every translation key used in Java existing in both bundles,
   - every `fx:id` and `onAction` in FXML matching its controller,
   - Discord's command name/description limits.
2. If you touched an FXML file or a controller, open **every** sidebar section once. FXML
   wiring is resolved at load time and the compiler cannot see it.
3. If you touched packaging, run the packaged self-test:

   ```bash
   mvn -Ppackage -Djpackage.type=app-image -Dskip.assets=true package -DskipTests
   ./target/dist/BotDealer/bin/BotDealer --self-test
   ```

## Code conventions

- Java 25, tabs for indentation, four-space tab width (matches the existing files).
- Lombok for entities; explicit constructors elsewhere.
- Services hold the rules and are testable without Spring or Discord. UI controllers and
  Discord handlers only translate.
- Anything user-visible gets an i18n key in **both** bundles, added through the same
  commit.
- Never log a token. The redaction converters are a safety net, not an excuse.

## Where things live

| Package | Responsibility |
|---|---|
| `config` | Application paths, bundled assets, Jackson, scheduler |
| `security` | Credential stores, token validation, redaction patterns |
| `domain` / `repository` | JPA entities and Spring Data repositories |
| `service.economy` | Wallets, ledger, daily rewards, per-guild settings |
| `service.betting` | Event lifecycle, bet placement, payout arithmetic |
| `service.music` | LavaPlayer playback, queue, engine routing, yt-dlp |
| `service.tts` | Piper synthesis, voices, sanitization, speech queue |
| `service.discord` | JDA lifecycle, commands, embeds, announcements |
| `ui` | JavaFX screens, dialogs and view models |
| `diagnostics` | `--self-test` and the build-time asset fetcher |

More detail in [ARCHITECTURE.md](ARCHITECTURE.md).

## Commits

Short, descriptive messages, in the style of the existing history. One logical change per
commit, and never a commit that leaves `mvn verify` failing.

## Reporting bugs

Open an issue with the `--self-test` output and the relevant log lines. **Never paste a
real token**; the log already masks them.
