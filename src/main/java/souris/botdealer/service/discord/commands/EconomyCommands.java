/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.math.BigDecimal;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import souris.botdealer.domain.Wallet;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.discord.DiscordMessages;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;
import souris.botdealer.service.economy.DailyRewardService;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.economy.WalletService;
import souris.botdealer.util.Money;

/** {@code /chorizos balance|daily}: a member's own economy. */
@Component
public class EconomyCommands extends AbstractCommandHandler {

	public static final String ROOT = "chorizos";

	private final WalletService wallets;
	private final DailyRewardService daily;
	private final UiEventBus uiEvents;

	public EconomyCommands(I18nService i18n, GuildConfigService guildConfig, WalletService wallets,
			DailyRewardService daily, UiEventBus uiEvents) {
		super(i18n, guildConfig);
		this.wallets = wallets;
		this.daily = daily;
		this.uiEvents = uiEvents;
	}

	@Override
	public String rootName() {
		return ROOT;
	}

	@Override
	public java.util.List<CommandData> definitions() {
		return java.util.List.of(Commands.slash(ROOT, DiscordMessages.CMD_CHORIZOS)
			.addSubcommands(
				new SubcommandData("balance", DiscordMessages.CMD_CHORIZOS_BALANCE)
					.addOptions(new OptionData(OptionType.USER, "user", DiscordMessages.OPT_USER, false)),
				new SubcommandData("daily", DiscordMessages.CMD_CHORIZOS_DAILY)));
	}

	@Override
	public void handle(SlashCommandInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		if ("daily".equals(event.getSubcommandName())) {
			claimDaily(event, guildId);
			return;
		}
		showBalance(event, guildId);
	}

	private void showBalance(SlashCommandInteractionEvent event, long guildId) {
		OptionMapping target = event.getOption("user");
		User user = target == null ? event.getUser() : target.getAsUser();
		boolean self = user.getIdLong() == event.getUser().getIdLong();

		Wallet wallet = self
			? wallets.getOrCreate(guildId, user.getIdLong(), user.getEffectiveName())
			: wallets.find(guildId, user.getIdLong()).orElse(null);

		BigDecimal balance = wallet == null ? Money.ZERO : wallet.getBalance();
		String key = self ? DiscordMessages.REPLY_BALANCE : DiscordMessages.REPLY_BALANCE_OTHER;
		String reply = self
			? text(event, key, money(event, balance), currency(event))
			: text(event, key, user.getEffectiveName(), money(event, balance), currency(event));
		event.reply(reply).setEphemeral(true).queue();
	}

	private void claimDaily(SlashCommandInteractionEvent event, long guildId) {
		DailyRewardService.ClaimResult result = daily.claim(guildId, event.getUser().getIdLong(),
			event.getUser().getEffectiveName());

		String reply = switch (result.status()) {
			case CLAIMED -> text(event, DiscordMessages.REPLY_DAILY_CLAIMED, money(event, result.amount()),
				money(event, result.balance()));
			case ON_COOLDOWN -> text(event, DiscordMessages.REPLY_DAILY_COOLDOWN,
				"<t:" + result.nextEligibleAt().getEpochSecond() + ":R>");
			case DISABLED -> text(event, DiscordMessages.REPLY_DAILY_DISABLED);
		};
		event.reply(reply).setEphemeral(true).queue();
		if (result.claimed()) {
			uiEvents.publish(new UiEvents.EconomyChanged(guildId));
		}
	}
}
