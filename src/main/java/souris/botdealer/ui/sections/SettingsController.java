/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.config.AppPaths;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.settings.ResetService;
import souris.botdealer.ui.UiRouter;
import souris.botdealer.ui.UiUtils;

/**
 * Settings section: language, stored token, credential backend, data folder and the
 * destructive reset. The token value is never rendered, only its masked form.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SettingsController {

	private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

	@FXML
	private Label titleLabel;
	@FXML
	private Label languageTitle;
	@FXML
	private Button languageEnButton;
	@FXML
	private Button languageEsButton;
	@FXML
	private Label tokenTitle;
	@FXML
	private Label tokenValue;
	@FXML
	private Label tokenHint;
	@FXML
	private Button forgetTokenButton;
	@FXML
	private Label storageTitle;
	@FXML
	private Label storageValue;
	@FXML
	private Label storageHint;
	@FXML
	private Label dataFolderTitle;
	@FXML
	private Label dataFolderValue;
	@FXML
	private Button openFolderButton;
	@FXML
	private Label resetTitle;
	@FXML
	private Label resetBody;
	@FXML
	private Button resetButton;

	private final I18nService i18n;
	private final AppSettingsService settings;
	private final SecretService secrets;
	private final ResetService resetService;
	private final UiRouter router;

	public SettingsController(I18nService i18n, AppSettingsService settings, SecretService secrets,
			ResetService resetService, UiRouter router) {
		this.i18n = i18n;
		this.settings = settings;
		this.secrets = secrets;
		this.resetService = resetService;
		this.router = router;
	}

	public void initialize() {
		titleLabel.setText(i18n.get("settings.title"));
		languageTitle.setText(i18n.get("settings.language"));
		tokenTitle.setText(i18n.get("settings.token"));
		tokenHint.setText(i18n.get("settings.token.hint"));
		storageTitle.setText(i18n.get("settings.storage"));
		storageHint.setText(i18n.get("settings.storage.hint"));
		dataFolderTitle.setText(i18n.get("settings.dataFolder"));
		resetTitle.setText(i18n.get("settings.reset.title"));
		resetBody.setText(i18n.get("settings.reset.body"));
		forgetTokenButton.setText(i18n.get("settings.token.forget"));
		openFolderButton.setText(i18n.get("settings.openFolder"));
		resetButton.setText(i18n.get("settings.reset.button"));

		tokenValue.setText(secrets.isSet(SecretKey.DISCORD_TOKEN)
			? secrets.masked(SecretKey.DISCORD_TOKEN)
			: i18n.get("settings.token.none"));
		storageValue.setText(secrets.store().displayName());
		dataFolderValue.setText(AppPaths.dataDir().toString());
	}

	@FXML
	private void switchToEnglish() {
		switchLanguage(Locale.ENGLISH);
	}

	@FXML
	private void switchToSpanish() {
		switchLanguage(Locale.of("es"));
	}

	private void switchLanguage(Locale locale) {
		if (locale.getLanguage().equals(i18n.getLocale().getLanguage())) {
			return;
		}
		settings.setLanguage(locale);
		i18n.setLocale(locale);
		router.reload();
	}

	@FXML
	private void forgetToken() {
		if (!UiUtils.confirm(i18n.get("settings.token.forget.confirm.title"),
			i18n.get("settings.token.forget.confirm.header"),
			i18n.get("settings.token.forget.confirm.body"))) {
			return;
		}
		resetService.forgetCredentials();
		tokenValue.setText(i18n.get("settings.token.none"));
		log.info("Discord token forgotten by the user");
	}

	@FXML
	private void openDataFolder() {
		Path folder = AppPaths.dataDir();
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				Desktop.getDesktop().open(folder.toFile());
				return;
			}
		} catch (Exception e) {
			log.debug("Could not open the data folder with the desktop API");
		}
		UiUtils.copyToClipboard(folder.toString());
	}

	@FXML
	private void resetApplication() {
		if (!UiUtils.confirm(i18n.get("settings.reset.confirm.title"),
			i18n.get("settings.reset.confirm.header"),
			i18n.get("settings.reset.confirm.body"))) {
			return;
		}
		resetService.resetEverything();
		UiUtils.helpDialog(i18n.get("settings.reset.done.title"), i18n.get("settings.reset.done.body"))
			.showAndWait();
		resetService.restartApplication();
	}
}
