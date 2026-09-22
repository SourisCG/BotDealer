/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.settings.AppSettingsService;

/**
 * Owns the primary stage and swaps root views.
 *
 * <p>Changing the language reloads the whole root scene: it is the only way to
 * guarantee that every FXML {@code %key} and every controller string is refreshed,
 * and desktop apps do it in milliseconds. Shell content resets to the dashboard,
 * which is acceptable and predictable.</p>
 */
@Component
public class UiRouter {

	private static final Logger log = LoggerFactory.getLogger(UiRouter.class);

	private static final String[] STYLESHEETS = {
		"/css/base.css", "/css/components.css", "/css/effects.css"
	};

	private final FxViewLoader viewLoader;
	private final I18nService i18n;
	private final AppSettingsService settings;

	private Stage stage;
	private View currentView = View.WIZARD;

	public UiRouter(FxViewLoader viewLoader, I18nService i18n, AppSettingsService settings) {
		this.viewLoader = viewLoader;
		this.i18n = i18n;
		this.settings = settings;
	}

	public void init(Stage stage) {
		this.stage = stage;
	}

	/** Shows the first screen: wizard on a fresh install, shell afterwards. */
	public void showStartView() {
		show(settings.isOnboardingComplete() ? View.SHELL : View.WIZARD);
	}

	public void show(View view) {
		if (stage == null) {
			throw new IllegalStateException("UiRouter.init(stage) must be called before showing a view");
		}
		currentView = view;
		Parent root = viewLoader.load(view.fxmlPath());
		Scene scene = new Scene(root, stage.getWidth() > 0 ? stage.getWidth() : 980,
			stage.getHeight() > 0 ? stage.getHeight() : 640);
		for (String stylesheet : STYLESHEETS) {
			scene.getStylesheets().add(UiRouter.class.getResource(stylesheet).toExternalForm());
		}
		stage.setScene(scene);
		stage.setTitle(i18n.get("app.title"));
		log.debug("Showing view {}", view);
	}

	/** Re-renders the current root, used after a language change. */
	public void reload() {
		show(currentView);
	}

	public View currentView() {
		return currentView;
	}
}
