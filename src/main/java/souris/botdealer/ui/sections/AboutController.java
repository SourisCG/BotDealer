/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.ui.UiUtils;
import souris.botdealer.ui.shell.MainShellController;

/**
 * About screen: version, license and the attribution notice the GPL requires for the
 * bundled Piper/espeak-ng natives, JDA, LavaPlayer and friends.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AboutController {

	private static final String PROJECT_URL = "https://github.com/SourisCG/BotDealer";

	@FXML
	private Label titleLabel;
	@FXML
	private Label versionLabel;
	@FXML
	private Label licenseTitle;
	@FXML
	private Label licenseBody;
	@FXML
	private Label creditsTitle;
	@FXML
	private Label creditsBody;
	@FXML
	private Hyperlink projectLink;

	private final I18nService i18n;

	public AboutController(I18nService i18n) {
		this.i18n = i18n;
	}

	public void initialize() {
		titleLabel.setText(i18n.get("about.title"));
		versionLabel.setText(i18n.get("about.version", MainShellController.version()));
		licenseTitle.setText(i18n.get("about.license"));
		licenseBody.setText(i18n.get("about.license.body"));
		creditsTitle.setText(i18n.get("about.credits"));
		creditsBody.setText(i18n.get("about.credits.body"));
		projectLink.setText(PROJECT_URL);
		projectLink.setOnAction(event -> UiUtils.openUrl(PROJECT_URL));
	}
}
