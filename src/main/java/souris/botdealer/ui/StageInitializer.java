/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Builds the primary stage once the FX toolkit is ready and asks the router for the
 * first screen (wizard on a fresh install, main shell afterwards).
 */
@Component
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

	private static final Logger log = LoggerFactory.getLogger(StageInitializer.class);

	private final UiRouter router;

	public StageInitializer(UiRouter router) {
		this.router = router;
	}

	@Override
	public void onApplicationEvent(StageReadyEvent event) {
		Stage stage = event.getStage();
		stage.setMinWidth(900);
		stage.setMinHeight(600);
		stage.setWidth(1020);
		stage.setHeight(680);
		router.init(stage);
		router.showStartView();
		stage.show();
		log.info("BotDealer UI started (first run: {})", router.currentView() == View.WIZARD);
	}
}
