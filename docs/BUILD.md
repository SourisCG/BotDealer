# Building and packaging BotDealer

> 🇪🇸 Resumen: `mvn verify` compila y prueba; `mvn -Ppackage -Djpackage.type=... package`
> genera un instalador autocontenido (rpm, deb, exe o app-image) que ya incluye su propio
> Java, así que el usuario final no instala nada.

## Requirements

- **JDK 25** (JDAVE, the Discord voice encryption library, requires it).
- **Maven 3.9+**.
- To build installers: a JDK whose `jlink` works (any standard Temurin/Liberica/Zulu build).
- Platform tools jpackage shells out to:
  - `.rpm` → `rpmbuild` (`sudo dnf install rpm-build`)
  - `.deb` → `fakeroot` + `dpkg-deb` (`sudo apt-get install -y fakeroot`)
  - `.exe` → WiX Toolset 3.x (`candle.exe`/`light.exe`); preinstalled on GitHub's
    `windows-latest` runners

## Everyday commands

```bash
mvn verify                 # compile + unit tests
mvn javafx:run             # run the desktop app from sources
mvn -Ppackage -Djpackage.type=app-image package   # portable folder under target/dist
```

## Installers

```bash
mvn -Ppackage -Djpackage.type=rpm package   # Fedora / RHEL
mvn -Ppackage -Djpackage.type=deb package   # Debian / Mint / Ubuntu
mvn -Ppackage -Djpackage.type=exe package   # Windows installer
```

Artifacts land in `target/dist/`. The application image bundles its own Java runtime
(jlink), so end users install nothing else.

### Versioning

`--app-version` comes from `${app.version}`, which defaults to the project version.
Release builds override it from the tag:

```bash
mvn -Ppackage -Djpackage.type=rpm -Dapp.version=1.2.3 package
```

### Using a different JDK for packaging

`jpackage` runs from `${jpackage.home}`, which defaults to the JDK running Maven.
Some Linux distributions ship a modified `java.security` and `jlink` then refuses to
build a runtime image ("java.security has been modified"). Point the build at a clean
JDK instead:

```bash
mvn -Ppackage -Djpackage.type=rpm -Djpackage.home=/opt/jdk-25 package
```

## How the image is assembled

1. `maven-jar-plugin` writes the **plain** application jar (the Spring Boot fat jar is
   skipped: jpackage needs a normal classpath jar, not nested jars).
2. `maven-dependency-plugin` copies every runtime dependency next to it in `target/app`.
3. `jpackage` builds a jlink runtime and a native launcher, then bundles `target/app`.

JavaFX is loaded from the **classpath** (the platform classifier jars Maven already
resolved). That works as of JavaFX 25 because the main class is deliberately *not* a
`javafx.application.Application` subclass — see `BotDealerApplication`.

The jlink runtime must contain the modules listed in `jpackage.modules`. `jdk.security.auth`
is easy to miss and matters: without it the freedesktop keychain backend cannot load
`com.sun.security.auth.module.UnixSystem` and the packaged app silently falls back to the
encrypted file store.

## Release artifacts (CI)

`.github/workflows/release.yml` builds, on every `v*` tag:

| Artifact | Platform |
|---|---|
| `BotDealer-<v>.exe` | Windows installer |
| `BotDealer-<v>.rpm` | Fedora / RHEL |
| `BotDealer-<v>.deb` | Debian / Mint / Ubuntu |
| `BotDealer-<v>-windows-app-image.zip` | Portable, no installation |
| `BotDealer-<v>-linux-app-image.tar.gz` | Portable, no installation |
| `botdealer-<v>.jar` | Portable jar, requires Java 25 |
| `SHA256SUMS` | Checksums for every artifact above |

Each platform job also runs the packaged app with `--self-test`, which boots Spring,
checks the database, the credential backend and the bundled assets, then exits with a
status code. That catches packaging regressions (like a missing jlink module) before a
release is published.
