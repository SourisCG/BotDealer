# BotDealer architecture (Phase 0)

> 🇪🇸 Este documento también existirá en español a partir de la fase 6 (documentación completa bilingüe).
> Por ahora, los comentarios de diseño clave están en ambos idiomas en el README y en el código.

## Runtime shape

BotDealer is a **non-modular** (classpath) Java 25 application combining:

- **Spring Boot 4** for configuration, persistence (H2 + JPA) and services. No web server
  (`spring.main.web-application-type=none`), no actuator, no H2 console.
- **JavaFX 25** for the desktop UI. JavaFX lives on the **module path** in packaged builds
  and on the **classpath** in dev builds; either way the main class never extends
  `javafx.application.Application` (required for fat-jar / jpackage startup).

## Startup sequence

```
BotDealerApplication.main()
  -> AppPaths.init()                 resolves %APPDATA%/BotDealer or ~/.local/share/BotDealer,
                                     sets botdealer.data.dir, creates db/logs/config
  -> Application.launch(FxApplication)
       -> FxApplication.init()       SpringApplicationBuilder(...).web(NONE).run(args)
       -> FxApplication.start(stage) publishes StageReadyEvent
       -> StageInitializer           loads /fxml/start.fxml via FxViewLoader,
                                     sets Scene + localized title, shows stage
       -> FxApplication.stop()       closes Spring context (later: JDA + executors)
```

Controllers are Spring beans: `FxViewLoader` sets `loader.setControllerFactory(context::getBean)`,
so constructor injection works in every controller.

## Threading contract (enforced from Phase 1 on)

- JavaFX Application Thread: UI only.
- JDA gateway/voice threads: Discord only, never touch JavaFX objects.
- Virtual-thread executors: repositories/services/TTS/music bookkeeping.
- Crossing boundary: `UiEventBus` events + `Platform.runLater`.

## i18n

`I18nService` (locale property + `ResourceBundle i18n/messages`): EN default, ES when the OS
language is Spanish, runtime switch via Settings/wizard. FXML uses `%key`; bot replies use
`i18n.get(key, args)`. `I18nParityTest` fails the build if EN/ES keys diverge.

## Configuration & secrets

- `application.properties` is local-only (gitignored); the committed `.example` documents every key.
- The Discord token is NEVER a property, NEVER in the DB, NEVER logged: Phase 1 stores it in
  the OS keychain with an AES-GCM file fallback, and `RedactingMessageConverter`/`RedactingThrowableConverter` (`%safeMsg`/`%safeEx`)
  redacts token-shaped strings in all appenders as a second line of defense.

## Data

H2 file database at `<dataDir>/db/botdealer`, `ddl-auto=update` for now. The Phase 3 schema
(`AppSetting`, `GuildConfig`, `User`, `Wallet` + `@Version`, `LedgerEntry`, `BetEvent`,
`BetOption`, `Bet`) replaces the carried-over placeholder entities.
