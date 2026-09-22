/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import souris.botdealer.BotDealerApplication;

/**
 * JavaFX application class. Kept separate from the main class on purpose:
 * a classpath-based JavaFX app (fat jar / jpackage image) cannot start when the
 * main class extends {@link Application}.
 *
 * <p>Lifecycle: {@link #init()} boots Spring (no web server), {@link #start(Stage)}
 * publishes a {@link StageReadyEvent} consumed by {@link StageInitializer},
 * {@link #stop()} closes the Spring context which in turn shuts down the Discord
 * connection and executors (Phases 1+).</p>
 */
public class FxApplication extends Application {

	private ConfigurableApplicationContext context;

	@Override
	public void init() {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(BotDealerApplication.class);
		builder.web(WebApplicationType.NONE);
		// Optional user overrides live next to the data (and therefore writable), not in
		// the read-only app image. Same keys as the bundled application.properties.
		builder.properties("spring.config.additional-location=optional:file:"
			+ souris.botdealer.config.AppPaths.configDir() + "/");
		context = builder.run(getParameters().getRaw().toArray(new String[0]));
	}

	@Override
	public void start(Stage stage) {
		context.publishEvent(new StageReadyEvent(stage));
	}

	@Override
	public void stop() {
		if (context != null && context.isRunning()) {
			context.close();
		}
		Platform.exit();
	}
}
