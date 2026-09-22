# Installing BotDealer

> 🇪🇸 **Resumen:** bajá el archivo de tu sistema desde *Releases*, instalalo con doble clic
> y listo: los instaladores traen su propio Java. La app abre un asistente la primera vez
> y guarda todo en tu carpeta de usuario. Para desinstalar, usá el desinstalador normal
> del sistema y después borrá la carpeta de datos si querés empezar de cero.

## Windows

1. Download `BotDealer-<version>.exe`.
2. Run it. Windows SmartScreen may warn you because the installer is not code-signed:
   choose **More info** → **Run anyway**. The warning appears because the build has no
   commercial certificate, not because anything is wrong with the file.
3. The installer lets you choose the folder and creates Start Menu and desktop shortcuts.
4. BotDealer starts and shows the setup wizard.

Uninstall from *Settings → Apps*. Your data stays in `%APPDATA%\BotDealer` unless you
delete it.

## Fedora / RHEL

```bash
sudo dnf install ./BotDealer-<version>.rpm
```

Then launch **BotDealer** from the application menu, or run `botdealer` in a terminal.

```bash
sudo dnf remove botdealer        # uninstall
rm -rf ~/.local/share/BotDealer  # optional: also remove wallets, settings and voices
```

## Debian / Linux Mint / Ubuntu

```bash
sudo apt install ./BotDealer-<version>.deb
```

Uninstall:

```bash
sudo apt remove botdealer
rm -rf ~/.local/share/BotDealer  # optional
```

## No installation (portable)

Download `BotDealer-<version>-linux-app-image.tar.gz` (or the Windows `.zip`) and unpack
it anywhere, including a USB stick:

```bash
tar -xzf BotDealer-<version>-linux-app-image.tar.gz
./BotDealer/bin/BotDealer
```

A portable copy keeps its data in the same user folder as an installed one, so the two
share settings and wallets. To keep them apart, launch the portable copy with a different
data folder:

```bash
JAVA_TOOL_OPTIONS="-Dbotdealer.data.dir=/path/to/folder" ./BotDealer/bin/BotDealer
```

## Portable jar (needs Java 25)

```bash
java -jar botdealer-<version>.jar
```

This build expects a Java 25 runtime on the machine. The music engine works; offline
speech does too, as long as the platform is supported by the bundled natives.

## Where your data lives

| | |
|---|---|
| Windows | `%APPDATA%\BotDealer` |
| Linux / macOS | `~/.local/share/BotDealer` |

| Folder | Contents |
|---|---|
| `db/` | Wallets, events, bets, settings (H2 database) |
| `config/` | Optional overrides: drop an `application.properties` here |
| `logs/` | Rotating logs, with token-shaped text redacted |
| `secrets/` | Only if no OS keychain is available: AES-256-GCM encrypted credentials |
| `voices/` | Piper voices (three ship with the installer) |
| `engines/` | yt-dlp and Deno, seeded from the installer and self-updating |
| `cache/` | Downloaded audio and synthesized speech |

## Starting over

**Settings → Reset application** deletes every wallet, event, setting, credential and
downloaded voice, then restarts BotDealer as if it were the first time. There is no undo.

## Updating

Install the newer package over the old one; your data is untouched. Portable copies just
need to be unpacked again, and the app checks the releases page so you know when a new
version exists.
