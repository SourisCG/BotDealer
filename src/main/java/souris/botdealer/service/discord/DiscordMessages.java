/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

/**
 * Every translation key the Discord layer uses, in one place.
 *
 * <p>Centralising them makes the keys greppable and, more importantly, testable:
 * {@code DiscordMessagesTest} reflects over these constants and fails the build if one
 * of them is missing from the English or Spanish bundle. A typo would otherwise only
 * show up as {@code !key!} in front of a real user.</p>
 */
public final class DiscordMessages {

	private DiscordMessages() {
	}

	// ---------------------------------------------------------------- command names
	public static final String CMD_BOTDEALER = "discord.command.botdealer";
	public static final String CMD_BOTDEALER_HELP = "discord.command.botdealer.help";
	public static final String CMD_BOTDEALER_PING = "discord.command.botdealer.ping";
	public static final String CMD_BOTDEALER_VERSION = "discord.command.botdealer.version";

	public static final String CMD_CHORIZOS = "discord.command.chorizos";
	public static final String CMD_CHORIZOS_BALANCE = "discord.command.chorizos.balance";
	public static final String CMD_CHORIZOS_DAILY = "discord.command.chorizos.daily";

	public static final String CMD_BET = "discord.command.bet";
	public static final String CMD_BET_PLACE = "discord.command.bet.place";
	public static final String CMD_BET_LIST = "discord.command.bet.list";
	public static final String CMD_BET_MINE = "discord.command.bet.mine";

	public static final String CMD_EVENT = "discord.command.event";
	public static final String CMD_EVENT_CREATE = "discord.command.event.create";
	public static final String CMD_EVENT_CLOSE = "discord.command.event.close";
	public static final String CMD_EVENT_SETTLE = "discord.command.event.settle";
	public static final String CMD_EVENT_CANCEL = "discord.command.event.cancel";

	public static final String CMD_ECONOMY = "discord.command.economy";
	public static final String CMD_ECONOMY_GIVE = "discord.command.economy.give";
	public static final String CMD_ECONOMY_REMOVE = "discord.command.economy.remove";
	public static final String CMD_ECONOMY_SET = "discord.command.economy.set";

	// ---------------------------------------------------------------- music commands
	public static final String CMD_PLAY = "discord.command.play";
	public static final String CMD_PAUSE = "discord.command.pause";
	public static final String CMD_RESUME = "discord.command.resume";
	public static final String CMD_SKIP = "discord.command.skip";
	public static final String CMD_STOP = "discord.command.stop";
	public static final String CMD_QUEUE = "discord.command.queue";
	public static final String CMD_NOWPLAYING = "discord.command.nowplaying";
	public static final String CMD_VOLUME = "discord.command.volume";
	public static final String CMD_LOOP = "discord.command.loop";
	public static final String CMD_SHUFFLE = "discord.command.shuffle";
	public static final String CMD_DISCONNECT = "discord.command.disconnect";

	// ---------------------------------------------------------------- option names
	public static final String OPT_USER = "discord.option.user";
	public static final String OPT_EVENT = "discord.option.event";
	public static final String OPT_OPTION = "discord.option.option";
	public static final String OPT_AMOUNT = "discord.option.amount";
	public static final String OPT_TITLE = "discord.option.title";
	public static final String OPT_DESCRIPTION = "discord.option.description";
	public static final String OPT_OPTION1 = "discord.option.option1";
	public static final String OPT_OPTION2 = "discord.option.option2";
	public static final String OPT_OPTION3 = "discord.option.option3";
	public static final String OPT_OPTION4 = "discord.option.option4";
	public static final String OPT_OPTION5 = "discord.option.option5";
	public static final String OPT_MODE = "discord.option.mode";
	public static final String OPT_CLOSES_IN = "discord.option.closesIn";
	public static final String OPT_WINNER = "discord.option.winner";
	public static final String OPT_ODDS = "discord.option.odds";
	public static final String OPT_QUERY = "discord.option.query";
	public static final String OPT_LEVEL = "discord.option.level";
	public static final String OPT_LOOP_MODE = "discord.option.loopMode";

	// ---------------------------------------------------------------- replies
	public static final String REPLY_PING = "discord.reply.ping";
	public static final String REPLY_VERSION = "discord.reply.version";
	public static final String REPLY_HELP = "discord.reply.help";
	public static final String REPLY_HELP_TITLE = "discord.reply.help.title";
	public static final String REPLY_BALANCE = "discord.reply.balance";
	public static final String REPLY_BALANCE_OTHER = "discord.reply.balance.other";
	public static final String REPLY_DAILY_CLAIMED = "discord.reply.daily.claimed";
	public static final String REPLY_DAILY_COOLDOWN = "discord.reply.daily.cooldown";
	public static final String REPLY_DAILY_DISABLED = "discord.reply.daily.disabled";
	public static final String REPLY_BET_PLACED = "discord.reply.bet.placed";
	public static final String REPLY_BET_NONE = "discord.reply.bet.none";
	public static final String REPLY_BET_LIST = "discord.reply.bet.list";
	public static final String REPLY_EVENT_CREATED = "discord.reply.event.created";
	public static final String REPLY_EVENT_CLOSED = "discord.reply.event.closed";
	public static final String REPLY_EVENT_CANCELLED = "discord.reply.event.cancelled";
	public static final String REPLY_EVENT_SETTLED = "discord.reply.event.settled";
	public static final String REPLY_EVENT_LIST = "discord.reply.event.list";
	public static final String REPLY_EVENT_NONE = "discord.reply.event.none";
	public static final String REPLY_ECONOMY_GIVEN = "discord.reply.economy.given";
	public static final String REPLY_ECONOMY_REMOVED = "discord.reply.economy.removed";
	public static final String REPLY_ECONOMY_SET = "discord.reply.economy.set";
	public static final String REPLY_SEARCHING = "discord.reply.searching";

	// ---------------------------------------------------------------- errors
	public static final String ERROR_NOT_ADMIN = "discord.error.notAdmin";
	public static final String ERROR_GUILD_ONLY = "discord.error.guildOnly";
	public static final String ERROR_GENERIC = "discord.error.generic";
	public static final String ERROR_INVALID_AMOUNT = "discord.error.invalidAmount";

	// ---------------------------------------------------------------- embeds
	public static final String EMBED_EVENT_TITLE = "discord.embed.event.title";
	public static final String EMBED_EVENT_STATUS = "discord.embed.event.status";
	public static final String EMBED_EVENT_MODE = "discord.embed.event.mode";
	public static final String EMBED_EVENT_OPTIONS = "discord.embed.event.options";
	public static final String EMBED_EVENT_POOL = "discord.embed.event.pool";
	public static final String EMBED_EVENT_CLOSES = "discord.embed.event.closes";
	public static final String EMBED_EVENT_NO_CLOSE = "discord.embed.event.noClose";
	public static final String EMBED_EVENT_NO_BETS = "discord.embed.event.noBets";
	public static final String EMBED_EVENT_FOOTER = "discord.embed.event.footer";
	public static final String EMBED_EVENT_WINNER = "discord.embed.event.winner";
	public static final String EMBED_EVENT_CANCELLED = "discord.embed.event.cancelled";

	// ---------------------------------------------------------------- bot lifecycle
	public static final String BOT_STATUS_STOPPED = "bot.status.stopped";
	public static final String BOT_STATUS_STARTING = "bot.status.starting";
	public static final String BOT_STATUS_CONNECTED = "bot.status.connected";
	public static final String BOT_STATUS_ERROR = "bot.status.error";
	public static final String BOT_ERROR_NO_TOKEN = "bot.error.noToken";
	public static final String BOT_ERROR_INVALID_TOKEN = "bot.error.invalidToken";
	public static final String BOT_ERROR_INTENT = "bot.error.intentNotEnabled";
	public static final String BOT_ERROR_NETWORK = "bot.error.network";
	public static final String BOT_ERROR_ALREADY_RUNNING = "bot.error.alreadyRunning";
}
