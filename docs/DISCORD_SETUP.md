# Setting up the Discord bot

> 🇪🇸 **Resumen:** creá una aplicación en el Portal de Desarrolladores de Discord, sacá el
> token del bot y pegalo en el asistente de BotDealer (el botón *Probar conexión* lo
> valida antes de guardarlo). Después invitá el bot a tu servidor con los permisos de
> aplicaciones y de voz. El token es un secreto: quien lo tenga controla tu bot.

You need a Discord account and a server where you can manage applications.

## 1. Create the application

1. Open <https://discord.com/developers/applications>.
2. **New Application**, give it a name, accept the terms.
3. Open the **Bot** section in the left menu.

## 2. Get the token

In **Bot**, press **Reset Token** (or **Copy** if you just created the bot). The token
looks like three dot-separated parts:

```
YOUR_BOT_TOKEN_HERE
```

That value is the password of your bot:

- Never paste it into a website, a chat, a screenshot or an issue.
- If it ever leaks, press **Reset Token** immediately; the old one stops working.
- BotDealer stores it in your operating system keychain and only ever shows its last four
  characters.

## 3. Intents

BotDealer needs two intents, both enabled by default:

- **Server Members Intent** — not required, but enabling it makes member names appear
  correctly in the wallet list.
- **Message Content Intent** — *only* needed for reading messages aloud in a voice
  channel. It is a **privileged** intent: enable it under **Bot → Privileged Gateway
  Intents** before turning read-aloud on. BotDealer does not request it unless read-aloud
  is enabled, because asking for a disabled privileged intent makes the login fail.

## 4. Invite the bot

1. Open **OAuth2 → URL Generator**.
2. Scopes: `bot` and `applications.commands`.
3. Permissions:
   - **View Channels**, **Send Messages**, **Embed Links** (events and replies)
   - **Connect**, **Speak** (music and speech)
   - **Use Slash Commands** is implied by the scope
4. Open the generated URL and pick your server.

## 5. Connect BotDealer

1. Open the app, paste the token in the wizard (or **Settings → Discord token**).
2. Press **Test connection**. The app shows the bot name it reached, which confirms the
   token is valid before anything is stored.
3. Press **Connect** in the status bar.

Slash commands are registered per guild as soon as the bot sees the server, so they appear
immediately. If they do not show up, kick the bot and re-invite it: Discord caches command
lists per client.

## 6. Check it works

In your server:

```
/botdealer ping
/chorizos balance
```

You should get an answer within a second. If not, see
[TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Permissions inside the app

Administrative commands (`/event`, `/economy`) require one of:

- the **Manage Server** permission,
- the **Administrator** permission,
- being the **server owner**, or
- holding the **admin role** configured in the app under **Servers**.

The person running BotDealer is deliberately not special-cased: installing the bot on
someone else's server must not let its operator hand out that server's currency.

Music control (`/skip`, `/stop`, …) requires being in the same voice channel as the bot,
holding the configured **DJ role**, or having Manage Server.
