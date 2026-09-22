/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;

/**
 * Loads FXML views with Spring-managed controllers and the active i18n bundle.
 *
 * <p>Controllers must be Spring beans (e.g. {@code @Component}) so constructor
 * injection works: {@code loader.setControllerFactory(context::getBean)}.</p>
 */
@Component
public class FxViewLoader {

	private final ApplicationContext context;
	private final I18nService i18n;

	public FxViewLoader(ApplicationContext context, I18nService i18n) {
		this.context = context;
		this.i18n = i18n;
	}

	/**
	 * @param fxmlPath classpath path such as {@code /fxml/start.fxml}
	 * @return the loaded root node with its controller already injected
	 */
	public Parent load(String fxmlPath) {
		URL location = FxViewLoader.class.getResource(fxmlPath);
		if (location == null) {
			throw new IllegalArgumentException("FXML not found on classpath: " + fxmlPath);
		}
		FXMLLoader loader = new FXMLLoader(location, i18n.getBundle());
		loader.setControllerFactory(context::getBean);
		try {
			return loader.load();
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot load FXML: " + fxmlPath, e);
		}
	}
}
