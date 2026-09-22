/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.config.AppPaths;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.settings.MusicEngine;

/**
 * Landing screen: what is configured right now and what comes next. Real economy and
 * bot metrics arrive with Phase 3/4.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DashboardController {

	@FXML
	private Label titleLabel;
	@FXML
	private Label welcomeLabel;
	@FXML
	private Label botValue;
	@FXML
	private Label currencyValue;
	@FXML
	private Label engineValue;
	@FXML
	private Label ttsValue;
	@FXML
	private Label dataFolderValue;
	@FXML
	private Label nextStepsLabel;

	private final I18nService i18n;
	private final AppSettingsService settings;
	private final SecretService secrets;

	public DashboardController(I18nService i18n, AppSettingsService settings, SecretService secrets) {
		this.i18n = i18n;
		this.settings = settings;
		this.secrets = secrets;
	}

	public void initialize() {
		titleLabel.setText(i18n.get("dashboard.title"));
		welcomeLabel.setText(i18n.get("dashboard.welcome"));

		boolean tokenStored = secrets.isSet(SecretKey.DISCORD_TOKEN);
		botValue.setText(tokenStored ? i18n.get("dashboard.bot.configured") : i18n.get("dashboard.bot.missing"));

		String symbol = settings.get(AppSettingKey.CURRENCY_SYMBOL);
		String plural = settings.get(AppSettingKey.CURRENCY_PLURAL);
		currencyValue.setText(i18n.get("dashboard.currency.value", symbol, plural,
			settings.get(AppSettingKey.STARTING_BALANCE), settings.get(AppSettingKey.DAILY_AMOUNT)));

		engineValue.setText(i18n.get(MusicEngine.fromStored(settings.get(AppSettingKey.MUSIC_ENGINE)).titleKey()));

		ttsValue.setText(settings.getBoolean(AppSettingKey.TTS_ENABLED)
			? i18n.get("dashboard.tts.on")
			: i18n.get("dashboard.tts.off"));

		dataFolderValue.setText(AppPaths.dataDir().toString());
		nextStepsLabel.setText(i18n.get("dashboard.nextSteps"));
	}
}
