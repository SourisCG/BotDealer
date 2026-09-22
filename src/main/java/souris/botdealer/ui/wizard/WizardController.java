/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.wizard;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.config.AppPaths;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.security.SecretKey;
import souris.botdealer.security.SecretService;
import souris.botdealer.security.TokenValidation;
import souris.botdealer.security.TokenValidator;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.settings.MusicEngine;
import souris.botdealer.ui.UiRouter;
import souris.botdealer.ui.UiUtils;
import souris.botdealer.ui.View;

/**
 * Drives the first-run wizard: six steps inside one FXML, a shared footer and all
 * state in {@link WizardModel}.
 *
 * <p>Nothing is persisted until "Finish": the Discord token is validated first and
 * only then written to the OS keychain. Changing the language reloads the scene and
 * resumes on the same step because the step index lives in the singleton model.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WizardController {

	private static final Logger log = LoggerFactory.getLogger(WizardController.class);

	private static final String TOKEN_HELP_URL = "https://discord.com/developers/applications";

	/** Bundled Piper voices offered during setup (Phase 5 replaces this with the registry). */
	private static final Map<String, String> VOICE_LABEL_KEYS = new LinkedHashMap<>();

	static {
		VOICE_LABEL_KEYS.put("", "wizard.tts.voice.default");
		VOICE_LABEL_KEYS.put("es_ES-sharvard-medium", "wizard.tts.voice.esSharvard");
		VOICE_LABEL_KEYS.put("en_US-lessac-medium", "wizard.tts.voice.enLessac");
		VOICE_LABEL_KEYS.put("en_US-libritts_r-medium", "wizard.tts.voice.enLibritts");
	}

	private static final int STEP_COUNT = 6;
	private static final int STEP_LANGUAGE = 0;
	private static final int STEP_TOKEN = 1;
	private static final int STEP_ECONOMY = 2;
	private static final int STEP_MUSIC = 3;
	private static final int STEP_TTS = 4;
	private static final int STEP_FINISH = 5;

	private static final String[] TITLE_KEYS = {
		"wizard.language.title", "wizard.token.title", "wizard.economy.title",
		"wizard.music.title", "wizard.tts.title", "wizard.finish.title"
	};
	private static final String[] SUBTITLE_KEYS = {
		"wizard.language.subtitle", "wizard.token.subtitle", "wizard.economy.subtitle",
		"wizard.music.subtitle", "wizard.tts.subtitle", "wizard.finish.subtitle"
	};

	@FXML
	private Label stepIndicatorLabel;
	@FXML
	private Label titleLabel;
	@FXML
	private Label subtitleLabel;
	@FXML
	private StackPane stepContainer;
	@FXML
	private VBox stepLanguage;
	@FXML
	private VBox stepToken;
	@FXML
	private VBox stepEconomy;
	@FXML
	private VBox stepMusic;
	@FXML
	private VBox stepTts;
	@FXML
	private VBox stepFinish;

	@FXML
	private RadioButton languageEnButton;
	@FXML
	private RadioButton languageEsButton;

	@FXML
	private PasswordField tokenField;
	@FXML
	private Button testTokenButton;
	@FXML
	private ProgressIndicator tokenProgress;
	@FXML
	private Label tokenResultLabel;
	@FXML
	private Label tokenIdentityLabel;

	@FXML
	private TextField currencySingularField;
	@FXML
	private TextField currencyPluralField;
	@FXML
	private TextField currencySymbolField;
	@FXML
	private Spinner<Integer> startingBalanceSpinner;
	@FXML
	private Spinner<Integer> dailyAmountSpinner;
	@FXML
	private Spinner<Integer> dailyCooldownSpinner;

	@FXML
	private RadioButton engineAutoButton;
	@FXML
	private RadioButton engineDirectButton;
	@FXML
	private RadioButton engineYtdlpButton;

	@FXML
	private CheckBox ttsEnabledCheck;
	@FXML
	private ComboBox<String> ttsVoiceCombo;
	@FXML
	private Label ttsSpeedLabel;
	@FXML
	private Slider ttsSpeedSlider;

	@FXML
	private Label summaryLanguage;
	@FXML
	private Label summaryCurrency;
	@FXML
	private Label summaryEngine;
	@FXML
	private Label summaryTts;
	@FXML
	private Label summaryToken;
	@FXML
	private Label summaryDataFolder;
	@FXML
	private Label finishHintLabel;

	@FXML
	private Label statusLabel;
	@FXML
	private Button backButton;
	@FXML
	private Button nextButton;

	private final WizardModel model;
	private final I18nService i18n;
	private final AppSettingsService settings;
	private final SecretService secrets;
	private final TokenValidator tokenValidator;
	private final UiRouter router;

	private final ToggleGroup languageGroup = new ToggleGroup();
	private final ToggleGroup engineGroup = new ToggleGroup();

	public WizardController(WizardModel model, I18nService i18n, AppSettingsService settings,
			SecretService secrets, TokenValidator tokenValidator, UiRouter router) {
		this.model = model;
		this.i18n = i18n;
		this.settings = settings;
		this.secrets = secrets;
		this.tokenValidator = tokenValidator;
		this.router = router;
	}

	public void initialize() {
		if (model.getLanguage() == null) {
			model.setLanguage(i18n.getLocale());
		}
		configureSpinners();
		bindToggles();
		populateVoices();
		loadModelIntoControls();
		updateStep();
	}

	/**
	 * Spinner value factories are created here because FXML cannot instantiate the
	 * nested {@code SpinnerValueFactory.IntegerSpinnerValueFactory} type.
	 */
	private void configureSpinners() {
		startingBalanceSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
			0, 100_000_000, model.getStartingBalance().intValue()));
		dailyAmountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
			0, 1_000_000, model.getDailyAmount()));
		dailyCooldownSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
			1, 168, model.getDailyCooldownHours()));
	}

	// ------------------------------------------------------------------ steps

	@FXML
	private void goBack() {
		if (model.getStep() > 0) {
			model.setStep(model.getStep() - 1);
			updateStep();
		}
	}

	@FXML
	private void goNext() {
		if (model.getStep() == STEP_FINISH) {
			finish();
			return;
		}
		if (!validateCurrentStep()) {
			return;
		}
		model.setStep(model.getStep() + 1);
		updateStep();
	}

	private boolean validateCurrentStep() {
		switch (model.getStep()) {
			case STEP_ECONOMY -> {
				if (currencySingularField.getText().isBlank() || currencyPluralField.getText().isBlank()) {
					statusLabel.setText(i18n.get("wizard.economy.error.names"));
					return false;
				}
			}
			default -> {
				// other steps are always valid; the token may be skipped on purpose
			}
		}
		statusLabel.setText("");
		return true;
	}

	private void updateStep() {
		int step = model.getStep();
		stepIndicatorLabel.setText(i18n.get("wizard.stepIndicator", step + 1, STEP_COUNT));
		titleLabel.setText(i18n.get(TITLE_KEYS[step]));
		subtitleLabel.setText(i18n.get(SUBTITLE_KEYS[step]));

		show(stepLanguage, step == STEP_LANGUAGE);
		show(stepToken, step == STEP_TOKEN);
		show(stepEconomy, step == STEP_ECONOMY);
		show(stepMusic, step == STEP_MUSIC);
		show(stepTts, step == STEP_TTS);
		show(stepFinish, step == STEP_FINISH);

		backButton.setDisable(step == 0);
		nextButton.setText(step == STEP_FINISH ? i18n.get("wizard.finish") : i18n.get("wizard.next"));

		if (step == STEP_FINISH) {
			populateSummary();
		}
		statusLabel.setText("");
	}

	private static void show(VBox pane, boolean visible) {
		pane.setVisible(visible);
		pane.setManaged(visible);
	}

	// ------------------------------------------------------------------ token

	@FXML
	private void testToken() {
		String candidate = tokenField.getText();
		if (candidate == null || candidate.isBlank()) {
			tokenResultLabel.setText(i18n.get("wizard.token.result.empty"));
			return;
		}
		setTokenTesting(true);
		Task<TokenValidation> task = new Task<>() {
			@Override
			protected TokenValidation call() {
				return tokenValidator.validate(candidate);
			}
		};
		task.setOnSucceeded(event -> applyTokenValidation(task.getValue(), candidate));
		task.setOnFailed(event -> {
			setTokenTesting(false);
			tokenResultLabel.setText(i18n.get("wizard.token.result.network", ""));
		});
		Thread.ofVirtual().name("discord-token-check").start(task);
	}

	private void applyTokenValidation(TokenValidation validation, String candidate) {
		setTokenTesting(false);
		model.setTokenValidation(validation);
		if (validation.isValid()) {
			model.setToken(candidate.strip());
			model.setSkipToken(false);
			tokenResultLabel.setText(i18n.get(validation.messageKey()));
			tokenIdentityLabel.setText(i18n.get("wizard.token.identity",
				validation.identity().displayName(), validation.identity().username()));
			statusLabel.setText(i18n.get("wizard.token.saved"));
		} else {
			model.setToken("");
			tokenIdentityLabel.setText("");
			tokenResultLabel.setText(i18n.get(validation.messageKey(), validation.detail()));
		}
	}

	private void setTokenTesting(boolean testing) {
		testTokenButton.setDisable(testing);
		tokenProgress.setVisible(testing);
		if (testing) {
			tokenResultLabel.setText(i18n.get("wizard.token.testing"));
			tokenIdentityLabel.setText("");
		}
	}

	@FXML
	private void skipToken() {
		model.setSkipToken(true);
		model.setToken("");
		model.setTokenValidation(null);
		tokenField.clear();
		tokenResultLabel.setText(i18n.get("wizard.token.skipped"));
		tokenIdentityLabel.setText("");
		statusLabel.setText(i18n.get("wizard.token.skipped"));
	}

	@FXML
	private void openTokenHelp() {
		if (!UiUtils.openUrl(TOKEN_HELP_URL)) {
			UiUtils.helpDialog(i18n.get("wizard.token.help"), i18n.get("wizard.token.help.body", TOKEN_HELP_URL))
				.showAndWait();
		}
	}

	// ------------------------------------------------------------------ finish

	private void finish() {
		if (!validateCurrentStep()) {
			return;
		}
		persistChoices();
		model.reset();
		log.info("Setup wizard finished; onboarding complete");
		router.show(View.SHELL);
	}

	private void persistChoices() {
		settings.setLanguage(model.getLanguage() == null ? i18n.getLocale() : model.getLanguage());
		settings.set(AppSettingKey.CURRENCY_SINGULAR, currencySingularField.getText().strip());
		settings.set(AppSettingKey.CURRENCY_PLURAL, currencyPluralField.getText().strip());
		settings.set(AppSettingKey.CURRENCY_SYMBOL, currencySymbolField.getText().strip());
		settings.set(AppSettingKey.STARTING_BALANCE, Integer.toString(startingBalanceSpinner.getValue()));
		settings.set(AppSettingKey.DAILY_AMOUNT, Integer.toString(dailyAmountSpinner.getValue()));
		settings.set(AppSettingKey.DAILY_COOLDOWN_HOURS, Integer.toString(dailyCooldownSpinner.getValue()));
		settings.set(AppSettingKey.MUSIC_ENGINE, selectedEngine().name());
		settings.set(AppSettingKey.TTS_ENABLED, Boolean.toString(ttsEnabledCheck.isSelected()));
		settings.set(AppSettingKey.TTS_VOICE, ttsVoiceCombo.getValue() == null ? "" : ttsVoiceCombo.getValue());
		settings.set(AppSettingKey.TTS_SPEED, Double.toString(round1(ttsSpeedSlider.getValue())));

		if (model.hasValidatedToken()) {
			secrets.set(SecretKey.DISCORD_TOKEN, model.getToken());
			log.info("Discord token stored in {}", secrets.store().id());
		}
		settings.setOnboardingComplete(true);
	}

	// ------------------------------------------------------------------ helpers

	private void bindToggles() {
		languageEnButton.setToggleGroup(languageGroup);
		languageEsButton.setToggleGroup(languageGroup);
		languageGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
			if (newToggle == null) {
				return;
			}
			Locale chosen = newToggle == languageEsButton ? Locale.of("es") : Locale.ENGLISH;
			if (chosen.getLanguage().equals(i18n.getLocale().getLanguage())) {
				return;
			}
			model.setLanguage(chosen);
			settings.setLanguage(chosen);
			i18n.setLocale(chosen);
			router.reload();
		});

		engineAutoButton.setToggleGroup(engineGroup);
		engineDirectButton.setToggleGroup(engineGroup);
		engineYtdlpButton.setToggleGroup(engineGroup);
	}

	private void populateVoices() {
		VOICE_LABEL_KEYS.forEach((voiceId, labelKey) -> ttsVoiceCombo.getItems().add(voiceId));
		ttsVoiceCombo.setCellFactory(list -> new VoiceListCell(i18n));
		ttsVoiceCombo.setButtonCell(new VoiceListCell(i18n));
	}

	private void loadModelIntoControls() {
		if ("es".equals(i18n.getLocale().getLanguage())) {
			languageEsButton.setSelected(true);
		} else {
			languageEnButton.setSelected(true);
		}
		currencySingularField.setText(model.getCurrencySingular());
		currencyPluralField.setText(model.getCurrencyPlural());
		currencySymbolField.setText(model.getCurrencySymbol());
		startingBalanceSpinner.getValueFactory().setValue(model.getStartingBalance().intValue());
		dailyAmountSpinner.getValueFactory().setValue(model.getDailyAmount());
		dailyCooldownSpinner.getValueFactory().setValue(model.getDailyCooldownHours());

		switch (model.getMusicEngine()) {
			case YOUTUBE_DIRECT -> engineDirectButton.setSelected(true);
			case YTDLP -> engineYtdlpButton.setSelected(true);
			default -> engineAutoButton.setSelected(true);
		}

		ttsEnabledCheck.setSelected(model.isTtsEnabled());
		ttsVoiceCombo.setValue(model.getTtsVoice());
		ttsSpeedSlider.setValue(model.getTtsSpeed());
		updateSpeedLabel();
		ttsSpeedSlider.valueProperty().addListener((observable, oldValue, newValue) -> updateSpeedLabel());
	}

	private void updateSpeedLabel() {
		ttsSpeedLabel.setText(i18n.get("wizard.tts.speed", round1(ttsSpeedSlider.getValue())));
	}

	private MusicEngine selectedEngine() {
		if (engineDirectButton.isSelected()) {
			return MusicEngine.YOUTUBE_DIRECT;
		}
		if (engineYtdlpButton.isSelected()) {
			return MusicEngine.YTDLP;
		}
		return MusicEngine.AUTO;
	}

	private void populateSummary() {
		Locale language = model.getLanguage() == null ? i18n.getLocale() : model.getLanguage();
		summaryLanguage.setText(language.getDisplayLanguage(language));
		summaryCurrency.setText(i18n.get("wizard.finish.currencyValue",
			currencySymbolField.getText(), currencyPluralField.getText(),
			startingBalanceSpinner.getValue(), dailyAmountSpinner.getValue(),
			dailyCooldownSpinner.getValue()));
		MusicEngine engine = selectedEngine();
		summaryEngine.setText(i18n.get(engine.titleKey()));
		summaryTts.setText(ttsEnabledCheck.isSelected()
			? i18n.get("wizard.finish.ttsValue", voiceLabel(ttsVoiceCombo.getValue()),
				round1(ttsSpeedSlider.getValue()))
			: i18n.get("wizard.finish.ttsDisabled"));
		summaryToken.setText(model.hasValidatedToken()
			? i18n.get("wizard.finish.tokenSet", validationName())
			: i18n.get("wizard.finish.tokenSkipped"));
		summaryDataFolder.setText(AppPaths.dataDir().toString());
		finishHintLabel.setText(i18n.get("wizard.finish.hint"));
	}

	private String validationName() {
		TokenValidation validation = model.getTokenValidation();
		return validation != null && validation.identity() != null
			? validation.identity().displayName()
			: "";
	}

	private String voiceLabel(String voiceId) {
		String labelKey = VOICE_LABEL_KEYS.get(voiceId == null ? "" : voiceId);
		return labelKey == null ? i18n.get("wizard.tts.voice.default") : i18n.get(labelKey);
	}

	private static double round1(double value) {
		return BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
	}

	/** Renders a voice id as a friendly, localized name in the combo box. */
	private static final class VoiceListCell extends javafx.scene.control.ListCell<String> {

		private final I18nService i18n;

		private VoiceListCell(I18nService i18n) {
			this.i18n = i18n;
		}

		@Override
		protected void updateItem(String voiceId, boolean empty) {
			super.updateItem(voiceId, empty);
			if (empty || voiceId == null) {
				setText(null);
				return;
			}
			String labelKey = VOICE_LABEL_KEYS.get(voiceId);
			setText(labelKey == null ? voiceId : i18n.get(labelKey));
		}
	}
}
