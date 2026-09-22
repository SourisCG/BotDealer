/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;
import souris.botdealer.domain.GuildConfig;
import souris.botdealer.service.economy.GuildConfigService;

/**
 * Who may create events and move other people's money.
 *
 * <p>Allowed: the guild owner, anyone with Manage Server or Administrator, or anyone
 * holding the role configured in the guild's settings. The app operator is deliberately
 * <em>not</em> special-cased: a bot installed on someone else's server must not let its
 * owner hand out that server's currency.</p>
 */
@Component
public class BotPermissions {

	private final GuildConfigService guildConfig;

	public BotPermissions(GuildConfigService guildConfig) {
		this.guildConfig = guildConfig;
	}

	public boolean canManage(SlashCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		if (guild == null || member == null) {
			return false;
		}
		if (guild.getOwnerIdLong() == member.getIdLong()) {
			return true;
		}
		if (member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)
				|| member.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
			return true;
		}
		Long adminRole = guildConfig.find(guild.getIdLong())
			.map(GuildConfig::getAdminRoleId)
			.orElse(null);
		return adminRole != null && member.getRoles().stream()
			.anyMatch(role -> role.getIdLong() == adminRole);
	}
}
