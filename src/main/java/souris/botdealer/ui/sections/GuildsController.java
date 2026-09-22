/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import java.math.BigDecimal;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.domain.GuildConfig;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.ui.UiSelectionModel;

/**
 * Per-guild overrides.
 *
 * <p>An empty field means "use the application default", which is shown as a prompt so
 * the operator can tell the difference between an override and an inherited value.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GuildsController {

	@FXML
	private Label titleLabel;
	@FXML
	private ComboBox<UiSelectionModel.GuildChoice> guildCombo;
	@FXML
	private Button saveButton;
	@FXML
	private Label hintLabel;
	@FXML
	private Label economyTitle;
	@FXML
	private Label singularLabel;
	@FXML
	private TextField singularField;
	@FXML
	private Label pluralLabel;
	@FXML
	private TextField pluralField;
	@FXML
	private Label symbolLabel;
	@FXML
	private TextField symbolField;
	@FXML
	private Label startingBalanceLabel;
	@FXML
	private TextField startingBalanceField;
	@FXML
	private Label dailyAmountLabel;
	@FXML
	private TextField dailyAmountField;
	@FXML
	private Label dailyCooldownLabel;
	@FXML
	private TextField dailyCooldownField;
	@FXML
	private Label minBetLabel;
	@FXML
	private TextField minBetField;
	@FXML
	private Label maxBetLabel;
	@FXML
	private TextField maxBetField;
	@FXML
	private Label moderationTitle;
	@FXML
	private Label localeLabel;
	@FXML
	private ComboBox<String> localeCombo;
	@FXML
	private Label adminRoleLabel;
	@FXML
	private TextField adminRoleField;
	@FXML
	private Label announceChannelLabel;
	@FXML
	private TextField announceChannelField;
	@FXML
	private Label bettingChannelLabel;
	@FXML
	private TextField bettingChannelField;
	@FXML
	private Label statusLabel;

	private final I18nService i18n;
	private final GuildConfigService guildConfig;
	private final AppSettingsService appSettings;
	private final UiSelectionModel selection;

	public GuildsController(I18nService i18n, GuildConfigService guildConfig, AppSettingsService appSettings,
			UiSelectionModel selection, UiEventBus uiEvents) {
		this.i18n = i18n;
		this.guildConfig = guildConfig;
		this.appSettings = appSettings;
		this.selection = selection;
		uiEvents.subscribe(event -> {
			if (event instanceof UiEvents.BotStatusChanged) {
				loadSelectedGuild();
			}
		});
	}

	public void initialize() {
		titleLabel.setText(i18n.get("sections.guilds.title"));
		saveButton.setText(i18n.get("guilds.save"));
		hintLabel.setText(i18n.get("guilds.hint"));
		economyTitle.setText(i18n.get("guilds.economy"));
		moderationTitle.setText(i18n.get("guilds.moderation"));
		singularLabel.setText(i18n.get("wizard.economy.singular"));
		pluralLabel.setText(i18n.get("wizard.economy.plural"));
		symbolLabel.setText(i18n.get("wizard.economy.symbol"));
		startingBalanceLabel.setText(i18n.get("wizard.economy.startingBalance"));
		dailyAmountLabel.setText(i18n.get("wizard.economy.dailyAmount"));
		dailyCooldownLabel.setText(i18n.get("wizard.economy.dailyCooldown"));
		minBetLabel.setText(i18n.get("guilds.minBet"));
		maxBetLabel.setText(i18n.get("guilds.maxBet"));
		localeLabel.setText(i18n.get("settings.language"));
		adminRoleLabel.setText(i18n.get("guilds.adminRole"));
		announceChannelLabel.setText(i18n.get("guilds.announceChannel"));
		bettingChannelLabel.setText(i18n.get("guilds.bettingChannel"));

		localeCombo.getItems().setAll("", "en", "es");
		guildCombo.setItems(selection.guilds());
		guildCombo.valueProperty().bindBidirectional(selection.selectedProperty());
		guildCombo.valueProperty().addListener((observable, oldValue, newValue) -> loadSelectedGuild());
		loadSelectedGuild();
	}

	private void loadSelectedGuild() {
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			GuildConfig config = guildConfig.getOrCreate(guildId);
			singularField.setText(nullSafe(config.getCurrencySingular()));
			pluralField.setText(nullSafe(config.getCurrencyPlural()));
			symbolField.setText(nullSafe(config.getCurrencySymbol()));
			startingBalanceField.setText(decimalText(config.getStartingBalance()));
			dailyAmountField.setText(integerText(config.getDailyAmount()));
			dailyCooldownField.setText(integerText(config.getDailyCooldownHours()));
			minBetField.setText(decimalText(config.getMinBet()));
			maxBetField.setText(decimalText(config.getMaxBet()));
			localeCombo.setValue(config.getLocale() == null ? "" : config.getLocale());
			adminRoleField.setText(idText(config.getAdminRoleId()));
			announceChannelField.setText(idText(config.getAnnounceChannelId()));
			bettingChannelField.setText(idText(config.getBettingChannelId()));
			statusLabel.setText("");
		}, () -> statusLabel.setText(i18n.get("dashboard.noGuild")));
	}

	@FXML
	private void save() {
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			try {
				GuildConfig config = guildConfig.getOrCreate(guildId);
				config.setCurrencySingular(blankToNull(singularField.getText()));
				config.setCurrencyPlural(blankToNull(pluralField.getText()));
				config.setCurrencySymbol(blankToNull(symbolField.getText()));
				config.setStartingBalance(decimalOrNull(startingBalanceField.getText()));
				config.setDailyAmount(integerOrNull(dailyAmountField.getText()));
				config.setDailyCooldownHours(integerOrNull(dailyCooldownField.getText()));
				config.setMinBet(decimalOrNull(minBetField.getText()));
				config.setMaxBet(decimalOrNull(maxBetField.getText()));
				config.setLocale(blankToNull(localeCombo.getValue()));
				config.setAdminRoleId(idOrNull(adminRoleField.getText()));
				config.setAnnounceChannelId(idOrNull(announceChannelField.getText()));
				config.setBettingChannelId(idOrNull(bettingChannelField.getText()));
				guildConfig.save(config);
				statusLabel.setText(i18n.get("guilds.saved"));
			} catch (NumberFormatException e) {
				statusLabel.setText(i18n.get("guilds.error.number"));
			}
		}, () -> statusLabel.setText(i18n.get("dashboard.noGuild")));
	}

	// ------------------------------------------------------------------ helpers

	private static String nullSafe(String value) {
		return value == null ? "" : value;
	}

	private static String decimalText(BigDecimal value) {
		return value == null ? "" : value.toPlainString();
	}

	private static String integerText(Integer value) {
		return value == null ? "" : value.toString();
	}

	private static String idText(Long value) {
		return value == null ? "" : value.toString();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static BigDecimal decimalOrNull(String value) {
		return value == null || value.isBlank() ? null : new BigDecimal(value.strip().replace(',', '.'));
	}

	private static Integer integerOrNull(String value) {
		return value == null || value.isBlank() ? null : Integer.valueOf(value.strip());
	}

	private static Long idOrNull(String value) {
		return value == null || value.isBlank() ? null : Long.valueOf(value.strip());
	}

	/** Kept for symmetry with the other screens. */
	String appDefaultCurrency() {
		return appSettings.get(AppSettingKey.CURRENCY_PLURAL);
	}

	/** Locale currently used by the app, for the picker hint. */
	Locale appLocale() {
		return i18n.getLocale();
	}
}
