/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import java.math.BigDecimal;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.economy.WalletService;
import souris.botdealer.util.Money;

/** {@code /economy give|remove|set} for guild managers. */
@Component
public class EconomyAdminCommands extends AbstractCommandHandler {

	public static final String ROOT = "economy";

	private final WalletService wallets;
	private final BotPermissions permissions;
	private final UiEventBus uiEvents;

	public EconomyAdminCommands(I18nService i18n, GuildConfigService guildConfig, WalletService wallets,
			BotPermissions permissions, UiEventBus uiEvents) {
		super(i18n, guildConfig);
		this.wallets = wallets;
		this.permissions = permissions;
		this.uiEvents = uiEvents;
	}

	@Override
	public String rootName() {
		return ROOT;
	}

	@Override
	public CommandData definition() {
		OptionData target = new OptionData(OptionType.USER, "user", DiscordMessages.OPT_USER, true);
		OptionData amount = new OptionData(OptionType.NUMBER, "amount", DiscordMessages.OPT_AMOUNT, true)
			.setMinValue(0.01);
		return Commands.slash(ROOT, DiscordMessages.CMD_ECONOMY)
			.addSubcommands(
				new SubcommandData("give", DiscordMessages.CMD_ECONOMY_GIVE).addOptions(target, amount),
				new SubcommandData("remove", DiscordMessages.CMD_ECONOMY_REMOVE)
					.addOptions(new OptionData(OptionType.USER, "user", DiscordMessages.OPT_USER, true),
						new OptionData(OptionType.NUMBER, "amount", DiscordMessages.OPT_AMOUNT, true)
							.setMinValue(0.01)),
				new SubcommandData("set", DiscordMessages.CMD_ECONOMY_SET)
					.addOptions(new OptionData(OptionType.USER, "user", DiscordMessages.OPT_USER, true),
						new OptionData(OptionType.NUMBER, "amount", DiscordMessages.OPT_AMOUNT, true)
							.setMinValue(0)));
	}

	@Override
	public void handle(SlashCommandInteractionEvent event) {
		if (!permissions.canManage(event)) {
			event.reply(text(event, DiscordMessages.ERROR_NOT_ADMIN)).setEphemeral(true).queue();
			return;
		}
		long guildId = event.getGuild().getIdLong();
		User target = event.getOption("user").getAsUser();
		BigDecimal amount = BigDecimal.valueOf(event.getOption("amount").getAsDouble());

		String reply = switch (String.valueOf(event.getSubcommandName())) {
			case "give" -> {
				Wallet wallet = wallets.deposit(guildId, target.getIdLong(), target.getEffectiveName(),
					amount, souris.botdealer.domain.LedgerReason.ADMIN_GRANT, null, event.getUser().getIdLong());
				yield text(event, DiscordMessages.REPLY_ECONOMY_GIVEN, target.getEffectiveName(),
					money(event, amount), money(event, wallet.getBalance()));
			}
			case "remove" -> {
				Wallet wallet = wallets.withdraw(guildId, target.getIdLong(), target.getEffectiveName(),
					amount, souris.botdealer.domain.LedgerReason.ADMIN_REMOVE, null,
					event.getUser().getIdLong());
				yield text(event, DiscordMessages.REPLY_ECONOMY_REMOVED, target.getEffectiveName(),
					money(event, amount), money(event, wallet.getBalance()));
			}
			default -> {
				Wallet current = wallets.getOrCreate(guildId, target.getIdLong(), target.getEffectiveName());
				BigDecimal delta = Money.normalize(amount).subtract(Money.normalize(current.getBalance()));
				Wallet wallet = wallets.adjust(guildId, target.getIdLong(), target.getEffectiveName(), delta,
					event.getUser().getIdLong());
				yield text(event, DiscordMessages.REPLY_ECONOMY_SET, target.getEffectiveName(),
					money(event, wallet.getBalance()));
			}
		};

		event.reply(reply).setEphemeral(true).queue();
		uiEvents.publish(new UiEvents.EconomyChanged(guildId));
	}
}
