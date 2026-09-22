/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.config.AppPaths;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;
import souris.botdealer.service.betting.EventQueryService;
import souris.botdealer.service.discord.BotStatus;
import souris.botdealer.service.discord.DiscordBotService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;
import souris.botdealer.service.economy.WalletService;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.settings.MusicEngine;
import souris.botdealer.ui.UiSelectionModel;

/**
 * Landing screen: live bot state and the numbers that matter (guilds, money in
 * circulation, open events). Refreshes whenever the bot changes state or money moves.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DashboardController {

	@FXML
	private Label titleLabel;
	@FXML
	private Label welcomeLabel;
	@FXML
	private ComboBox<UiSelectionModel.GuildChoice> guildCombo;
	@FXML
	private Label botValue;
	@FXML
	private Button botButton;
	@FXML
	private Label guildsValue;
	@FXML
	private Label currencyValue;
	@FXML
	private Label eventsValue;
	@FXML
	private Label dataFolderValue;
	@FXML
	private Label nextStepsLabel;

	private final I18nService i18n;
	private final AppSettingsService settings;
	private final SecretService secrets;
	private final DiscordBotService bot;
	private final UiSelectionModel selection;
	private final WalletService wallets;
	private final EventQueryService events;

	public DashboardController(I18nService i18n, AppSettingsService settings, SecretService secrets,
			DiscordBotService bot, UiSelectionModel selection, WalletService wallets,
			EventQueryService events, UiEventBus uiEvents) {
		this.i18n = i18n;
		this.settings = settings;
		this.secrets = secrets;
		this.bot = bot;
		this.selection = selection;
		this.wallets = wallets;
		this.events = events;
		uiEvents.subscribe(event -> {
			if (event instanceof UiEvents.BotStatusChanged || event instanceof UiEvents.EconomyChanged
					|| event instanceof UiEvents.EventChanged) {
				refresh();
			}
		});
	}

	public void initialize() {
		titleLabel.setText(i18n.get("dashboard.title"));
		welcomeLabel.setText(i18n.get("dashboard.welcome"));
		guildCombo.setItems(selection.guilds());
		guildCombo.valueProperty().bindBidirectional(selection.selectedProperty());
		guildCombo.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
		dataFolderValue.setText(AppPaths.dataDir().toString());
		nextStepsLabel.setText(i18n.get("dashboard.nextSteps"));
		refresh();
	}

	@FXML
	private void toggleBot() {
		if (bot.status() == BotStatus.CONNECTED || bot.status() == BotStatus.STARTING) {
			bot.stop();
		} else {
			bot.startAsync();
		}
	}

	private void refresh() {
		BotStatus status = bot.status();
		boolean tokenStored = secrets.isSet(SecretKey.DISCORD_TOKEN);
		botValue.setText(tokenStored ? i18n.get(status.labelKey()) : i18n.get("dashboard.bot.missing"));
		botButton.setText(status == BotStatus.CONNECTED ? i18n.get("shell.bot.disconnect")
			: i18n.get("shell.bot.connect"));
		botButton.setDisable(!tokenStored || status == BotStatus.STARTING);

		guildsValue.setText(i18n.get("dashboard.guilds.value", bot.guilds().size()));

		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			String symbol = settings.get(AppSettingKey.CURRENCY_SYMBOL);
			currencyValue.setText(i18n.get("dashboard.currency.value", symbol,
				wallets.totalInCirculation(guildId).toPlainString(),
				wallets.count(guildId)));
			eventsValue.setText(i18n.get("dashboard.events.value",
				events.openEventCount(guildId), events.listByGuild(guildId).size()));
		}, () -> {
			currencyValue.setText(i18n.get("dashboard.noGuild"));
			eventsValue.setText(i18n.get("dashboard.noGuild"));
		});

		// The engine label lives in the status bar, but keep it meaningful here too.
		String engine = i18n.get(MusicEngine.fromStored(settings.get(AppSettingKey.MUSIC_ENGINE)).titleKey());
		nextStepsLabel.setText(i18n.get("dashboard.nextSteps") + " · " + engine);
	}
}
