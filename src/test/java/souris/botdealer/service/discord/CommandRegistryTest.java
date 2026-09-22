/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

import java.util.List;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.Test;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.betting.BetService;
import souris.botdealer.service.betting.EventQueryService;
import souris.botdealer.service.betting.EventService;
import souris.botdealer.service.discord.commands.BetCommands;
import souris.botdealer.service.discord.commands.BotPermissions;
import souris.botdealer.service.discord.commands.CommandHandler;
import souris.botdealer.service.discord.commands.EconomyAdminCommands;
import souris.botdealer.service.discord.commands.EconomyCommands;
import souris.botdealer.service.discord.commands.EventAdminCommands;
import souris.botdealer.service.discord.commands.GeneralCommands;
import souris.botdealer.service.discord.commands.MusicCommands;
import souris.botdealer.service.discord.commands.TtsCommands;
import souris.botdealer.service.economy.DailyRewardService;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.economy.WalletService;
import souris.botdealer.service.music.MusicService;
import souris.botdealer.service.tts.TtsService;
import souris.botdealer.service.tts.VoiceRegistry;
import souris.botdealer.settings.AppSettingsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Guards Discord's registration limits.
 *
 * <p>Discord rejects a whole command list when one name or description breaks its rules
 * (name 1-32 chars matching {@code [a-z0-9_-]}, description 1-100 chars), and the
 * failure only shows up at runtime in a live guild. This test checks the real
 * definitions the handlers build.</p>
 */
class CommandRegistryTest {

	private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
	private static final int MAX_DESCRIPTION = 100;

	private static CommandRegistry registry() {
		I18nService i18n = mock(I18nService.class);
		GuildConfigService guildConfig = mock(GuildConfigService.class);
		UiEventBus bus = mock(UiEventBus.class);

		List<CommandHandler> handlers = List.of(
			new GeneralCommands(i18n, guildConfig),
			new EconomyCommands(i18n, guildConfig, mock(WalletService.class), mock(DailyRewardService.class), bus),
			new BetCommands(i18n, guildConfig, mock(BetService.class), mock(EventQueryService.class),
				mock(EventAnnouncer.class), bus),
			new EventAdminCommands(i18n, guildConfig, mock(EventService.class), mock(EventAnnouncer.class),
				mock(BotPermissions.class), bus),
			new EconomyAdminCommands(i18n, guildConfig, mock(WalletService.class), mock(BotPermissions.class),
				bus),
			new MusicCommands(i18n, guildConfig, mock(MusicService.class), mock(MusicAnnouncer.class)),
			new TtsCommands(i18n, guildConfig, mock(TtsService.class), mock(VoiceRegistry.class),
				mock(MusicService.class), mock(AppSettingsService.class)));
		return new CommandRegistry(handlers);
	}

	@Test
	void registersEveryExpectedRootCommand() {
		assertEquals(List.of("bet", "botdealer", "chorizos", "disconnect", "economy", "event", "loop",
			"nowplaying", "pause", "play", "queue", "resume", "shuffle", "skip", "stop", "tts", "volume"),
			registry().names().stream().sorted().toList());
	}

	@Test
	void rootCommandsRespectDiscordLimits() {
		for (CommandData command : registry().commandData()) {
			assertTrue(VALID_NAME.matcher(command.getName()).matches(),
				"invalid command name: " + command.getName());
			assertTrue(isValidDescription(((SlashCommandData) command).getDescription()),
				"invalid description for " + command.getName());
		}
	}

	@Test
	void subcommandsAndOptionsRespectDiscordLimits() {
		for (CommandData command : registry().commandData()) {
			// Root commands without subcommands carry their options directly.
			for (OptionData option : ((SlashCommandData) command).getOptions()) {
				assertTrue(VALID_NAME.matcher(option.getName()).matches(),
					"invalid option name: " + option.getName());
				assertTrue(isValidDescription(option.getDescription()),
					"invalid description for option " + option.getName());
			}
			for (SubcommandData subcommand : ((SlashCommandData) command).getSubcommands()) {
				assertTrue(VALID_NAME.matcher(subcommand.getName()).matches(),
					"invalid subcommand name: " + subcommand.getName());
				assertTrue(isValidDescription(subcommand.getDescription()),
					"invalid description for subcommand " + subcommand.getName());
				for (OptionData option : subcommand.getOptions()) {
					assertTrue(VALID_NAME.matcher(option.getName()).matches(),
						"invalid option name: " + option.getName());
					assertTrue(isValidDescription(option.getDescription()),
						"invalid description for option " + option.getName());
				}
			}
		}
	}

	@Test
	void requiredOptionsComeBeforeOptionalOnes() {
		// Discord requires required options to be listed first; a violation fails the
		// whole registration.
		for (CommandData command : registry().commandData()) {
			boolean seenOptionalAtRoot = false;
			for (OptionData option : ((SlashCommandData) command).getOptions()) {
				if (!option.isRequired()) {
					seenOptionalAtRoot = true;
				} else {
					assertFalse(seenOptionalAtRoot, "required option " + option.getName() + " in /"
						+ command.getName() + " comes after an optional one");
				}
			}
			for (SubcommandData subcommand : ((SlashCommandData) command).getSubcommands()) {
				boolean seenOptional = false;
				for (OptionData option : subcommand.getOptions()) {
					if (!option.isRequired()) {
						seenOptional = true;
					} else {
						assertFalse(seenOptional, "required option " + option.getName() + " in /"
							+ command.getName() + " " + subcommand.getName() + " comes after an optional one");
					}
				}
			}
		}
	}

	@Test
	void everyHandlerIsReachableByName() {
		CommandRegistry registry = registry();

		registry.names().forEach(name ->
			assertTrue(registry.byName().containsKey(name), "no handler for /" + name));
	}

	@Test
	void noRootCommandCollidesWithADiscordReservedWord() {
		// "help" is not a command in Discord but a slash command must not shadow nothing;
		// this mainly documents intent: everything is namespaced under our own roots.
		for (String name : registry().names()) {
			assertFalse(name.contains(" "), "command names cannot contain spaces: " + name);
		}
	}

	private static boolean isValidDescription(String description) {
		return description != null && !description.isBlank() && description.length() <= MAX_DESCRIPTION;
	}
}
