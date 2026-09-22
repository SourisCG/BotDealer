# FAQ

> 🇪🇸 **Resumen:** respuestas cortas a las preguntas más frecuentes sobre dinero, tokens,
> música y privacidad.

**Is the money real?**
No. Chorizos are play money with no value, and nothing in BotDealer can be exchanged for
anything real.

**Can I rename the currency?**
Yes. In the setup wizard, or later under **Servers** for a specific server.

**How do members get money?**
They receive the starting balance the first time they use the bot, they can claim a
periodic reward with `/chorizos daily`, and managers can grant or remove money. Every
movement is recorded in the ledger.

**Where is my Discord token?**
In your operating system keychain (Windows Credential Manager, macOS Keychain, or the
freedesktop Secret Service). If no keychain is available, in an AES-256-GCM encrypted file
inside your data folder. Never in the database, never in a log, never in the repository.
See [SECURITY.md](SECURITY.md).

**Can I see the token again?**
Only its last four characters, to identify which one is stored. That is intentional.

**Does the bot work in several servers at once?**
Yes. Each server has its own wallets, events and settings, and the bot handles them
independently. Music and speech are per server too.

**Do I need to install anything else?**
No. The installers bundle their own Java runtime, the music engines and the speech voices.
The portable `.jar` is the only build that needs Java on the machine.

**Does the desktop app have to stay open?**
Yes: the app *is* the bot. Closing it disconnects the bot from Discord.

**Can several people manage events?**
Yes. Anyone with Manage Server, Administrator, the server owner, or the configured admin
role.

**What happens if nobody bet on the winning option?**
Every stake is refunded instead of the pot disappearing.

**Can I change the odds after people have bet?**
Fixed odds are frozen per bet: editing an option later cannot change what an existing bet
is worth.

**Why is the installer so large?**
Because nothing is downloaded behind your back: the Java runtime, three speech voices
(~208 MB) and the music engines (~120 MB) are all inside. `-Dskip.assets=true` builds a
much smaller installer that fetches the engines and voices on first use.

**Does it work offline?**
The app, the economy and offline speech work without internet. Discord and YouTube
obviously do not.

**Is my text sent anywhere?**
Speech is synthesized locally; nothing leaves your machine. The only outbound traffic is to
Discord, YouTube, and — when you ask for it — voice downloads and engine updates.

**Which platforms are supported?**
Windows and Linux (x86-64 and arm64). macOS is not built or tested, although the libraries
support it.

**Can I use it with a bot account I already have?**
Yes. Paste any bot token; the app validates it before storing it.

**The bot does not respond. Where do I look?**
The status bar, then `<dataDir>/logs/botdealer.log`, then
[TROUBLESHOOTING.md](TROUBLESHOOTING.md).
