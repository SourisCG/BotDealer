/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.tts.PiperTtsEngine;
import souris.botdealer.service.tts.TtsEvents;
import souris.botdealer.service.tts.TtsService;
import souris.botdealer.service.tts.VoiceCatalog;
import souris.botdealer.service.tts.VoiceDescriptor;
import souris.botdealer.service.tts.VoiceDownloader;
import souris.botdealer.service.tts.VoiceRegistry;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.ui.UiSelectionModel;
import souris.botdealer.ui.UiUtils;

/**
 * Text to speech screen: installed voices, the download library, speech settings and a
 * test phrase that is spoken through Discord.
 *
 * <p>Voices are large files, so the library shows the size and streams progress. The
 * engine card explains why speech is unavailable instead of leaving the buttons dead.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TtsController {

	private static final Logger log = LoggerFactory.getLogger(TtsController.class);

	@FXML
	private Label titleLabel;
	@FXML
	private Label engineStatusLabel;
	@FXML
	private Label voicesTitle;
	@FXML
	private Button defaultButton;
	@FXML
	private Button previewButton;
	@FXML
	private Button exportButton;
	@FXML
	private Button deleteButton;
	@FXML
	private TableView<VoiceRow> voiceTable;
	@FXML
	private TableColumn<VoiceRow, String> voiceIdColumn;
	@FXML
	private TableColumn<VoiceRow, String> voiceLanguageColumn;
	@FXML
	private TableColumn<VoiceRow, String> voiceSpeakersColumn;
	@FXML
	private TableColumn<VoiceRow, String> voiceSizeColumn;
	@FXML
	private TableColumn<VoiceRow, String> voiceDefaultColumn;
	@FXML
	private Label testTitle;
	@FXML
	private TextField testField;
	@FXML
	private Button speakButton;
	@FXML
	private Label statusLabel;
	@FXML
	private Label settingsTitle;
	@FXML
	private Label speedLabel;
	@FXML
	private Slider speedSlider;
	@FXML
	private CheckBox readAloudCheck;
	@FXML
	private CheckBox duckCheck;
	@FXML
	private Label readAloudHint;
	@FXML
	private Label downloadTitle;
	@FXML
	private ListView<VoiceCatalog.CatalogVoice> catalogList;
	@FXML
	private Button downloadButton;
	@FXML
	private ProgressBar downloadProgress;
	@FXML
	private Label engineTitle;
	@FXML
	private Label engineDetail;
	@FXML
	private Button openVoicesButton;
	@FXML
	private Button clearCacheButton;

	/** One row of the installed-voice table. */
	public record VoiceRow(VoiceDescriptor voice, String sizeLabel, String defaultLabel) {
	}

	private final I18nService i18n;
	private final VoiceRegistry voices;
	private final VoiceCatalog catalog;
	private final VoiceDownloader downloader;
	private final TtsService tts;
	private final PiperTtsEngine engine;
	private final AppSettingsService settings;
	private final UiSelectionModel selection;

	public TtsController(I18nService i18n, VoiceRegistry voices, VoiceCatalog catalog,
			VoiceDownloader downloader, TtsService tts, PiperTtsEngine engine,
			AppSettingsService settings, UiSelectionModel selection, UiEventBus uiEvents) {
		this.i18n = i18n;
		this.voices = voices;
		this.catalog = catalog;
		this.downloader = downloader;
		this.tts = tts;
		this.engine = engine;
		this.settings = settings;
		this.selection = selection;
		uiEvents.subscribe(event -> {
			if (event instanceof TtsEvents) {
				refresh();
			}
		});
	}

	public void initialize() {
		titleLabel.setText(i18n.get("sections.tts.title"));
		voicesTitle.setText(i18n.get("tts.installed"));
		testTitle.setText(i18n.get("tts.test"));
		settingsTitle.setText(i18n.get("tts.settings"));
		downloadTitle.setText(i18n.get("tts.download"));
		engineTitle.setText(i18n.get("tts.engine"));
		defaultButton.setText(i18n.get("tts.setDefault"));
		previewButton.setText(i18n.get("tts.preview"));
		exportButton.setText(i18n.get("tts.export"));
		deleteButton.setText(i18n.get("tts.delete"));
		speakButton.setText(i18n.get("tts.speak"));
		downloadButton.setText(i18n.get("tts.downloadSelected"));
		openVoicesButton.setText(i18n.get("tts.openVoices"));
		clearCacheButton.setText(i18n.get("tts.clearCache"));
		readAloudCheck.setText(i18n.get("tts.readAloud"));
		duckCheck.setText(i18n.get("tts.duck"));
		readAloudHint.setText(i18n.get("tts.readAloud.hint"));
		testField.setText(i18n.get("tts.testPhrase"));

		voiceIdColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().voice().id()));
		voiceLanguageColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(cell.getValue().voice().languageLabel()));
		voiceSpeakersColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(cell.getValue().voice().isMultiSpeaker()
				? i18n.get("tts.speakers.many", cell.getValue().voice().speakers().size())
				: i18n.get("tts.speakers.one")));
		voiceSizeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().sizeLabel()));
		voiceDefaultColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().defaultLabel()));

		catalogList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(VoiceCatalog.CatalogVoice voice, boolean empty) {
				super.updateItem(voice, empty);
				setText(empty || voice == null ? null
					: voice.displayName() + " · " + megabytes(voice.sizeBytes()));
			}
		});

		speedSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
			settings.set(AppSettingKey.TTS_SPEED, Double.toString(round1(newValue.doubleValue())));
			updateSpeedLabel();
		});
		readAloudCheck.selectedProperty().addListener((observable, oldValue, newValue) ->
			settings.set(AppSettingKey.TTS_READ_ALOUD, Boolean.toString(newValue)));
		duckCheck.selectedProperty().addListener((observable, oldValue, newValue) ->
			settings.set(AppSettingKey.TTS_DUCK, Boolean.toString(newValue)));

		speedSlider.setValue(settings.getDouble(AppSettingKey.TTS_SPEED));
		readAloudCheck.setSelected(settings.getBoolean(AppSettingKey.TTS_READ_ALOUD));
		duckCheck.setSelected(settings.getBoolean(AppSettingKey.TTS_DUCK));
		updateSpeedLabel();
		refresh();
	}

	// ------------------------------------------------------------------ voices

	private void refresh() {
		var installed = voices.voices();
		String configured = settings.get(AppSettingKey.TTS_VOICE);
		String effective = tts.voiceFor(selection.selectedGuildId().orElse(0L)).map(VoiceDescriptor::id)
			.orElse(configured);

		var rows = installed.stream().map(voice -> new VoiceRow(voice, megabytes(voice.sizeBytes()),
			voice.id().equals(effective) ? i18n.get("tts.isDefault") : "")).toList();
		voiceTable.setItems(FXCollections.observableArrayList(rows));

		var downloadable = catalog.voices().stream()
			.filter(voice -> !downloader.isInstalled(voice))
			.toList();
		catalogList.setItems(FXCollections.observableArrayList(downloadable));
		downloadButton.setDisable(downloadable.isEmpty());

		boolean ready = engine.isAvailable();
		engineStatusLabel.setText(i18n.get(ready ? "tts.engine.ready" : "tts.engine.unavailable"));
		engineDetail.setText(ready
			? i18n.get("tts.engine.detail", installed.size())
			: i18n.get("tts.engine.reason", engine.unavailableReason()));
		speakButton.setDisable(installed.isEmpty());
	}

	@FXML
	private void setDefault() {
		VoiceRow row = voiceTable.getSelectionModel().getSelectedItem();
		if (row == null) {
			statusLabel.setText(i18n.get("tts.error.selectVoice"));
			return;
		}
		settings.set(AppSettingKey.TTS_VOICE, row.voice().id());
		statusLabel.setText(i18n.get("tts.defaultSet", row.voice().id()));
		refresh();
	}

	/** Speaks the test phrase with the default voice, in the selected guild. */
	@FXML
	private void speak() {
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			if (tts.speak(guildId, testField.getText())) {
				statusLabel.setText(i18n.get("tts.speaking"));
			}
		}, () -> statusLabel.setText(i18n.get("dashboard.noGuild")));
	}

	@FXML
	private void preview() {
		VoiceRow row = voiceTable.getSelectionModel().getSelectedItem();
		if (row == null) {
			statusLabel.setText(i18n.get("tts.error.selectVoice"));
			return;
		}
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			settings.set(AppSettingKey.TTS_VOICE, row.voice().id());
			tts.speak(guildId, testField.getText());
			statusLabel.setText(i18n.get("tts.previewing"));
		}, () -> statusLabel.setText(i18n.get("dashboard.noGuild")));
	}

	/** Writes the synthesized phrase next to the voices folder, for testing without Discord. */
	@FXML
	private void exportWav() {
		VoiceRow row = voiceTable.getSelectionModel().getSelectedItem();
		if (row == null) {
			statusLabel.setText(i18n.get("tts.error.selectVoice"));
			return;
		}
		try {
			Path produced = tts.synthesizeToFile(testField.getText(), row.voice());
			Path target = voices.voicesDir().resolve("export.wav");
			Files.createDirectories(target.getParent());
			Files.copy(produced, target, StandardCopyOption.REPLACE_EXISTING);
			statusLabel.setText(i18n.get("tts.exported", target.toString()));
		} catch (Exception e) {
			log.warn("WAV export failed: {}", e.toString());
			statusLabel.setText(i18n.get("tts.error.export"));
		}
	}

	@FXML
	private void deleteVoice() {
		VoiceRow row = voiceTable.getSelectionModel().getSelectedItem();
		if (row == null) {
			statusLabel.setText(i18n.get("tts.error.selectVoice"));
			return;
		}
		if (!UiUtils.confirm(i18n.get("tts.delete.confirm.title"), i18n.get("tts.delete.confirm.header"),
			i18n.get("tts.delete.confirm.body", row.voice().id()))) {
			return;
		}
		if (voices.delete(row.voice().id())) {
			statusLabel.setText(i18n.get("tts.deleted", row.voice().id()));
		}
		refresh();
	}

	// ------------------------------------------------------------------ download

	@FXML
	private void downloadVoice() {
		VoiceCatalog.CatalogVoice selected = catalogList.getSelectionModel().getSelectedItem();
		if (selected == null) {
			statusLabel.setText(i18n.get("tts.error.selectDownload"));
			return;
		}
		downloadButton.setDisable(true);
		downloadProgress.setProgress(0);
		statusLabel.setText(i18n.get("tts.downloading", selected.displayName()));

		Thread.ofVirtual().name("voice-download").start(() -> {
			var installed = downloader.download(selected, progress -> Platform.runLater(() -> {
				downloadProgress.setProgress(progress.percent() / 100.0);
				if (progress.status() == VoiceDownloader.Progress.Status.VERIFYING) {
					statusLabel.setText(i18n.get("tts.verifying"));
				}
			}));
			Platform.runLater(() -> {
				downloadButton.setDisable(false);
				statusLabel.setText(installed.isPresent()
					? i18n.get("tts.downloaded", selected.displayName())
					: i18n.get("tts.error.download"));
				refresh();
			});
		});
	}

	// ------------------------------------------------------------------ engine

	@FXML
	private void openVoicesFolder() {
		Path folder = voices.voicesDir();
		try {
			Files.createDirectories(folder);
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				Desktop.getDesktop().open(folder.toFile());
				return;
			}
		} catch (Exception e) {
			log.debug("Could not open {}", folder);
		}
		UiUtils.copyToClipboard(folder.toString());
	}

	@FXML
	private void clearCache() {
		int removed = tts.clearCache();
		statusLabel.setText(i18n.get("tts.cacheCleared", removed));
	}

	private void updateSpeedLabel() {
		speedLabel.setText(i18n.get("wizard.tts.speed", round1(speedSlider.getValue())));
	}

	private static String megabytes(long bytes) {
		return String.format("%.1f MB", bytes / 1048576.0);
	}

	private static double round1(double value) {
		return java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
	}
}
