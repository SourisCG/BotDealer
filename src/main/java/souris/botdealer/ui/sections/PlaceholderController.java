/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.ui.Section;

/**
 * Generic "coming in a later phase" section, reused by Events, Wallets, Music and TTS
 * until their phases land. Configured right after each FXML load.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PlaceholderController {

	@FXML
	private Label sectionTitle;
	@FXML
	private Label sectionBody;
	@FXML
	private Label sectionPhase;

	private final I18nService i18n;

	public PlaceholderController(I18nService i18n) {
		this.i18n = i18n;
	}

	/** Fills the placeholder with the texts of the given section. */
	public void configure(Section section) {
		sectionTitle.setText(i18n.get("sections." + section.id() + ".title"));
		sectionBody.setText(i18n.get("sections." + section.id() + ".body"));
		sectionPhase.setText(i18n.get("sections.phase", i18n.get("sections." + section.id() + ".phase")));
	}
}
