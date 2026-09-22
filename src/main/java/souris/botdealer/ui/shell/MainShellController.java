/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.shell;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;
import souris.botdealer.service.discord.BotStatus;
import souris.botdealer.service.discord.DiscordBotService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.settings.MusicEngine;
import souris.botdealer.ui.FxViewLoader;
import souris.botdealer.ui.Section;
import souris.botdealer.ui.UiRouter;
import souris.botdealer.ui.sections.PlaceholderController;

/**
 * Sidebar navigation, status bar and the bot connect/disconnect control.
 *
 * <p>Section roots are cached so switching tabs does not rebuild the scene graph. Every
 * FXML controller is prototype-scoped, so the cached placeholder roots each keep their
 * own controller instance.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MainShellController {

	@FXML
	private VBox navBox;
	@FXML
	private StackPane contentArea;
	@FXML
	private Label botStatusLabel;
	@FXML
	private Label engineStatusLabel;
	@FXML
	private Label currencyStatusLabel;
	@FXML
	private Label storageStatusLabel;
	@FXML
	private Label versionLabel;
	@FXML
	private Button languageEnButton;
	@FXML
	private Button languageEsButton;
	@FXML
	private Button botToggleButton;

	private final FxViewLoader viewLoader;
	private final I18nService i18n;
	private final AppSettingsService settings;
	private final SecretService secrets;
	private final UiRouter router;
	private final DiscordBotService bot;

	private final Map<Section, Parent> cache = new EnumMap<>(Section.class);
	private final Map<Section, Button> navButtons = new EnumMap<>(Section.class);

	public MainShellController(FxViewLoader viewLoader, I18nService i18n, AppSettingsService settings,
			SecretService secrets, UiRouter router, DiscordBotService bot, UiEventBus uiEvents) {
		this.viewLoader = viewLoader;
		this.i18n = i18n;
		this.settings = settings;
		this.secrets = secrets;
		this.router = router;
		this.bot = bot;
		uiEvents.subscribe(event -> {
			if (event instanceof UiEvents.BotStatusChanged) {
				refreshBotStatus();
			}
		});
	}

	public void initialize() {
		buildNavigation();
		populateStaticStatus();
		refreshBotStatus();
		highlightLanguageButton();
		select(Section.DASHBOARD);
	}

	// ---------------------------------------------------------------- navigation

	private void buildNavigation() {
		navBox.getChildren().clear();
		navButtons.clear();
		for (Section section : Section.values()) {
			Button button = new Button(i18n.get(section.labelKey()));
			button.getStyleClass().add("nav-button");
			button.setMaxWidth(Double.MAX_VALUE);
			button.setTooltip(new Tooltip(i18n.get(section.labelKey())));
			button.setOnAction(event -> select(section));
			navButtons.put(section, button);
			navBox.getChildren().add(button);
		}
	}

	private void select(Section section) {
		navButtons.forEach((candidate, button) ->
			button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), candidate == section));

		Parent root = cache.computeIfAbsent(section, this::loadSection);
		contentArea.getChildren().setAll(root);
	}

	private Parent loadSection(Section section) {
		FxViewLoader.LoadedView loaded = viewLoader.loadWithController(section.fxmlPath());
		if (loaded.controller() instanceof PlaceholderController placeholder) {
			placeholder.configure(section);
		}
		return loaded.root();
	}

	// ---------------------------------------------------------------- bot control

	@FXML
	private void toggleBot() {
		if (bot.status() == BotStatus.CONNECTED || bot.status() == BotStatus.STARTING) {
			bot.stop();
		} else {
			bot.startAsync();
		}
	}

	private void refreshBotStatus() {
		BotStatus status = bot.status();
		String label = i18n.get(status.labelKey());
		if (status == BotStatus.ERROR && !bot.lastErrorKey().isBlank()) {
			label = label + " · " + i18n.get(bot.lastErrorKey());
		}
		botStatusLabel.setText(i18n.get("shell.status.bot", label));
		botToggleButton.setText(status == BotStatus.CONNECTED || status == BotStatus.STARTING
			? i18n.get("shell.bot.disconnect")
			: i18n.get("shell.bot.connect"));
		botToggleButton.setDisable(status == BotStatus.STARTING);
	}

	// ---------------------------------------------------------------- language

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

	private void highlightLanguageButton() {
		boolean spanish = "es".equals(i18n.getLocale().getLanguage());
		languageEnButton.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), !spanish);
		languageEsButton.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), spanish);
	}

	// ---------------------------------------------------------------- status bar

	private void populateStaticStatus() {
		engineStatusLabel.setText(i18n.get("shell.status.engine",
			i18n.get(MusicEngine.fromStored(settings.get(AppSettingKey.MUSIC_ENGINE)).titleKey())));
		currencyStatusLabel.setText(i18n.get("shell.status.currency",
			settings.get(AppSettingKey.CURRENCY_SYMBOL), settings.get(AppSettingKey.CURRENCY_PLURAL)));
		storageStatusLabel.setText(i18n.get("shell.status.storage", secrets.store().displayName()));
		versionLabel.setText(i18n.get("app.version", version()));
		if (!secrets.isSet(SecretKey.DISCORD_TOKEN)) {
			botToggleButton.setDisable(true);
			botToggleButton.setTooltip(new Tooltip(i18n.get("bot.error.noToken")));
		}
	}

	/** Implementation version is absent in dev runs, hence the fallback. */
	public static String version() {
		String version = MainShellController.class.getPackage().getImplementationVersion();
		return version == null ? "dev" : version;
	}
}
