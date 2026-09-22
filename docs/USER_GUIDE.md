# User guide

> 🇪🇸 **Resumen:** el asistente inicial configura el token, la moneda, el motor de música y
> la voz. Después, desde la barra lateral manejás todo: *Panel*, *Eventos*, *Carteras*,
> *Música*, *Texto a voz*, *Servidores*, *Ajustes* y *Acerca de*. El bot refleja lo mismo
> en Discord con comandos de barra. Podés cambiar el idioma con los botones EN/ES.

## The status bar

Always visible at the top:

| Item | Meaning |
|---|---|
| **Bot** | `Stopped`, `Connecting`, `Connected`, or the reason it failed. |
| **Music engine** | Which engine is selected. |
| **Currency** | The symbol and name in use. |
| **Credentials** | `OS keychain` or `Encrypted file`. |
| **Connect / Disconnect** | Starts or stops the Discord session. |

## Dashboard

Live state: bot status, number of servers, money in circulation, open events, and the data
folder. Pick a server at the top; every other screen follows that choice.

## Events

The betting feature. The desktop app is the full editor; Discord has the common cases.

**Creating an event**

1. **New event**.
2. Title, and an optional description.
3. **Payout**: *Shared pot* or *Fixed odds*.
   - *Shared pot*: everyone bets into one pool and the winners split it in proportion to
     their stake. Odds are implied by the pool and shown live.
   - *Fixed odds*: each option pays a multiplier fixed when the bet is placed. The creator
     carries the risk, so watch the liability hint.
4. Add at least two options. For fixed odds, each option needs odds above 1.
5. Closing time in minutes (`0` means you close it by hand) and an optional rake.
6. **Create** — the event opens immediately and a live card is posted in the channel.

**Running an event**

- The card updates as bets arrive: pools, bet counts and implied odds.
- **Close** stops accepting bets. Events with a closing time close themselves.
- **Settle** needs the winning option selected in the options table: winners are credited
  and losers lose their stake. This cannot be undone.
- **Cancel** returns every stake.

If nobody backed the winning option, every stake is refunded rather than the pot
disappearing.

## Wallets

Balances for the selected server, with search and a full audit trail.

- **Grant** / **Remove** add or take money.
- **Set** makes the balance exactly the number you type.
- The right-hand table is the ledger: every movement, with the reason and the balance
  after it. Nothing is ever edited or deleted, so a balance can always be explained.

## Music

Pick a voice channel, press **Connect**, then search or paste a link and press **Add to
queue**. The bot joins, plays and leaves when you tell it to.

| Control | What it does |
|---|---|
| Now playing | Artwork, title, author and a live progress bar. |
| Pause / Resume | Pauses playback. |
| Skip | Jumps to the next track. |
| Stop | Stops and empties the queue. |
| Shuffle / Loop | Shuffle, and repeat off → track → queue. |
| Volume | 0–200 %. |
| Queue | Numbered list, matching the numbers `/queue` shows. |

The engine card shows which engines are ready, their versions, and buttons to update them
or open the folder. The YouTube account card is optional; see [MUSIC.md](MUSIC.md).

## Text to speech

Offline neural voices that run on your own computer.

- **Installed voices**: pick one and **Set as default**, **Preview in Discord** (speaks
  the test phrase in your voice channel), **Export WAV** (writes `export.wav` next to your
  voices, useful for testing without Discord) or **Delete**.
- **More voices**: download additional ones with a progress bar. Voices are 60–80 MB.
- **Speech settings**: speed, whether the bot reads the voice channel chat aloud, and
  whether the music is lowered while it speaks.

Read-aloud only reads messages written in the text chat of the voice channel the bot is
in, and only from members in that channel. It needs the privileged Message Content intent
(see [DISCORD_SETUP.md](DISCORD_SETUP.md)). More detail: [TTS.md](TTS.md).

## Servers

Per-server overrides. **An empty field means "use the application default"**, which is why
the fields start empty.

| Setting | Effect |
|---|---|
| Currency names and symbol | Rename the money for this server. |
| Starting balance | What a new member receives on first use. |
| Daily reward and cooldown | The periodic top-up and how often it can be claimed. |
| Minimum / maximum bet | `0` maximum means unlimited. |
| Admin role id | Who may manage events and balances. |
| Announce / betting channel ids | Where results are announced and where bets are accepted. |

To get an id: enable Developer Mode in Discord, then right-click a role or channel and
choose *Copy ID*.

## Settings

Language, the stored token (masked, with **Forget token**), the credential backend, the
data folder shortcut, and **Reset application** — which deletes everything and returns to
the first-run wizard.

## Commands in Discord

| Command | What it does |
|---|---|
| `/botdealer help\|ping\|version` | Help card, latency, version. |
| `/chorizos balance [user]` | Your balance, or someone else's. |
| `/chorizos daily` | Claim the periodic reward. |
| `/bet place event option amount` | Place a bet. |
| `/bet list [event]` | Open events, or one event in detail. |
| `/bet mine` | Your bets. |
| `/event create\|close\|settle\|cancel` | Manage events (managers only). |
| `/economy give\|remove\|set` | Adjust balances (managers only). |
| `/play`, `/pause`, `/resume`, `/skip`, `/stop`, `/queue`, `/nowplaying`, `/volume`, `/loop`, `/shuffle`, `/disconnect` | Music. |
| `/tts say\|join\|leave\|voice\|on\|off\|status` | Speech. |

Every reply is in the server's language: the app language by default, overridable per
server under **Servers**.
