/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;

/**
 * Builds the primary stage once the FX toolkit is ready.
 *
 * <p>Phase 0 shows the boot screen. Phase 1 replaces it with the first-run setup
 * wizard or the main shell depending on the onboarding flag.</p>
 */
@Component
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

	private static final Logger log = LoggerFactory.getLogger(StageInitializer.class);

	private final FxViewLoader viewLoader;
	private final I18nService i18n;

	public StageInitializer(FxViewLoader viewLoader, I18nService i18n) {
		this.viewLoader = viewLoader;
		this.i18n = i18n;
	}

	@Override
	public void onApplicationEvent(StageReadyEvent event) {
		Stage stage = event.getStage();
		Parent root = viewLoader.load("/fxml/start.fxml");
		Scene scene = new Scene(root, 900, 600);
		stage.setScene(scene);
		stage.setTitle(i18n.get("app.title"));
		stage.setMinWidth(760);
		stage.setMinHeight(520);
		stage.show();
		log.info("BotDealer UI started (locale={})", i18n.getLocale());
	}
}
