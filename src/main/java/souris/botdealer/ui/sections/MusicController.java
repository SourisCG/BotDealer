/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.config.AppPaths;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.discord.DiscordBotService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.commands.MusicEmbeds;
import souris.botdealer.service.music.MusicEvents;
import souris.botdealer.service.music.MusicService;
import souris.botdealer.service.music.RepeatMode;
import souris.botdealer.service.music.engine.EngineInstaller;
import souris.botdealer.service.music.engine.EngineRouter;
import souris.botdealer.service.music.engine.EngineStatus;
import souris.botdealer.service.music.engine.EngineUpdater;
import souris.botdealer.service.music.engine.YouTubeOauthService;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.ui.FxViewLoader;
import souris.botdealer.ui.UiSelectionModel;
import souris.botdealer.ui.UiUtils;
import souris.botdealer.ui.dialogs.YoutubeOauthController;

/**
 * Music screen: transport controls, queue, engine status and the YouTube account.
 *
 * <p>The now-playing card refreshes on a one-second tick so the progress bar moves;
 * everything else reacts to {@link MusicEvents} from the service.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MusicController {

	private static final Logger log = LoggerFactory.getLogger(MusicController.class);

	@FXML
	private Label titleLabel;
	@FXML
	private ComboBox<UiSelectionModel.GuildChoice> guildCombo;
	@FXML
	private ComboBox<VoiceChannelChoice> channelCombo;
	@FXML
	private Button connectButton;
	@FXML
	private Button disconnectButton;
	@FXML
	private Label nowPlayingTitle;
	@FXML
	private ImageView artwork;
	@FXML
	private Label nowPlayingValue;
	@FXML
	private ProgressBar trackProgress;
	@FXML
	private Label trackTime;
	@FXML
	private Button pauseButton;
	@FXML
	private Button skipButton;
	@FXML
	private Button stopButton;
	@FXML
	private Button shuffleButton;
	@FXML
	private Button loopButton;
	@FXML
	private Label volumeLabel;
	@FXML
	private Slider volumeSlider;
	@FXML
	private Label volumeValue;
	@FXML
	private Label playbackStatus;
	@FXML
	private Label addLabel;
	@FXML
	private TextField queryField;
	@FXML
	private Button playButton;
	@FXML
	private Label queueTitle;
	@FXML
	private Button removeButton;
	@FXML
	private Button clearButton;
	@FXML
	private TableView<QueueRow> queueTable;
	@FXML
	private TableColumn<QueueRow, String> queueIndexColumn;
	@FXML
	private TableColumn<QueueRow, String> queueTitleColumn;
	@FXML
	private TableColumn<QueueRow, String> queueDurationColumn;
	@FXML
	private Label engineTitle;
	@FXML
	private Label engineStatus;
	@FXML
	private Label engineVersions;
	@FXML
	private Button updateEngineButton;
	@FXML
	private Button openEnginesButton;
	@FXML
	private Label engineResult;
	@FXML
	private Label accountTitle;
	@FXML
	private Label accountStatus;
	@FXML
	private Button linkAccountButton;
	@FXML
	private Button unlinkAccountButton;
	@FXML
	private Label accountHint;
	@FXML
	private Label localFilesTitle;
	@FXML
	private Label localFilesHint;
	@FXML
	private Button openMusicFolderButton;

	/** A voice channel of the selected guild. */
	public record VoiceChannelChoice(long id, String name) {
		@Override
		public String toString() {
			return name;
		}
	}

	/** One row of the queue table. */
	public record QueueRow(int index, String title, String duration) {
	}

	private final I18nService i18n;
	private final MusicService music;
	private final EngineRouter router;
	private final EngineUpdater updater;
	private final EngineInstaller installer;
	private final YouTubeOauthService oauth;
	private final DiscordBotService bot;
	private final AppSettingsService settings;
	private final UiSelectionModel selection;
	private final FxViewLoader viewLoader;

	private Timeline ticker;

	public MusicController(I18nService i18n, MusicService music, EngineRouter router, EngineUpdater updater,
			EngineInstaller installer, YouTubeOauthService oauth, DiscordBotService bot,
			AppSettingsService settings, UiSelectionModel selection, FxViewLoader viewLoader,
			UiEventBus uiEvents) {
		this.i18n = i18n;
		this.music = music;
		this.router = router;
		this.updater = updater;
		this.installer = installer;
		this.oauth = oauth;
		this.bot = bot;
		this.settings = settings;
		this.selection = selection;
		this.viewLoader = viewLoader;
		uiEvents.subscribe(event -> {
			if (event instanceof MusicEvents) {
				refresh();
			}
		});
	}

	public void initialize() {
		titleLabel.setText(i18n.get("sections.music.title"));
		nowPlayingTitle.setText(i18n.get("music.nowPlaying"));
		addLabel.setText(i18n.get("music.add"));
		queueTitle.setText(i18n.get("music.queue"));
		engineTitle.setText(i18n.get("music.engine.title"));
		accountTitle.setText(i18n.get("music.account.title"));
		accountHint.setText(i18n.get("music.account.hint"));
		localFilesTitle.setText(i18n.get("music.localFiles.title"));
		localFilesHint.setText(i18n.get("music.localFiles.hint"));
		volumeLabel.setText(i18n.get("music.volume"));
		connectButton.setText(i18n.get("music.connect"));
		disconnectButton.setText(i18n.get("music.disconnect"));
		playButton.setText(i18n.get("music.play"));
		removeButton.setText(i18n.get("music.remove"));
		clearButton.setText(i18n.get("music.clear"));
		updateEngineButton.setText(i18n.get("music.engine.update"));
		openEnginesButton.setText(i18n.get("music.engine.open"));
		linkAccountButton.setText(i18n.get("music.account.link"));
		unlinkAccountButton.setText(i18n.get("music.account.unlink"));
		openMusicFolderButton.setText(i18n.get("music.localFiles.open"));

		queueIndexColumn.setCellValueFactory(cell -> new SimpleStringProperty(Integer.toString(cell.getValue().index())));
		queueTitleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().title()));
		queueDurationColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().duration()));

		guildCombo.setItems(selection.guilds());
		guildCombo.valueProperty().bindBidirectional(selection.selectedProperty());
		guildCombo.valueProperty().addListener((observable, oldValue, newValue) -> onGuildChanged());
		volumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
			selection.selectedGuildId().ifPresent(guildId -> music.setVolume(guildId, newValue.intValue()));
			volumeValue.setText(newValue.intValue() + "%");
		});

		onGuildChanged();
		refreshEngineCard();
		refreshAccountCard();
		refresh();

		ticker = new Timeline(new KeyFrame(Duration.seconds(1), event -> refresh()));
		ticker.setCycleCount(Timeline.INDEFINITE);
		ticker.play();
	}

	private void onGuildChanged() {
		channelCombo.getItems().clear();
		selection.selectedGuildId().ifPresent(guildId -> bot.jda()
			.map(jda -> jda.getGuildById(guildId))
			.ifPresent(guild -> guild.getVoiceChannels().forEach(channel ->
				channelCombo.getItems().add(new VoiceChannelChoice(channel.getIdLong(), channel.getName())))));
		if (!channelCombo.getItems().isEmpty()) {
			channelCombo.getSelectionModel().selectFirst();
		}
		refresh();
	}

	// ------------------------------------------------------------------ transport

	@FXML
	private void connect() {
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			VoiceChannelChoice channel = channelCombo.getValue();
			if (channel == null) {
				playbackStatus.setText(i18n.get("music.error.pickChannel"));
				return;
			}
			if (!music.join(guildId, channel.id())) {
				playbackStatus.setText(i18n.get("music.error.joinFailed"));
			}
			refresh();
		}, () -> playbackStatus.setText(i18n.get("dashboard.noGuild")));
	}

	@FXML
	private void disconnect() {
		selection.selectedGuildId().ifPresent(guildId -> {
			music.leave(guildId);
			refresh();
		});
	}

	@FXML
	private void togglePause() {
		selection.selectedGuildId().ifPresent(guildId -> {
			if (music.isPaused(guildId)) {
				music.resume(guildId);
			} else {
				music.pause(guildId);
			}
			refresh();
		});
	}

	@FXML
	private void skip() {
		selection.selectedGuildId().ifPresent(guildId -> {
			music.skip(guildId);
			refresh();
		});
	}

	@FXML
	private void stop() {
		selection.selectedGuildId().ifPresent(guildId -> {
			music.stop(guildId);
			refresh();
		});
	}

	@FXML
	private void shuffle() {
		selection.selectedGuildId().ifPresent(guildId -> {
			music.toggleShuffle(guildId);
			refresh();
		});
	}

	@FXML
	private void loop() {
		selection.selectedGuildId().ifPresent(guildId -> {
			music.cycleRepeat(guildId);
			refresh();
		});
	}

	@FXML
	private void play() {
		selection.selectedGuildId().ifPresent(guildId -> {
			String query = queryField.getText();
			if (query == null || query.isBlank()) {
				playbackStatus.setText(i18n.get("music.error.emptyQuery"));
				return;
			}
			// The desktop operator is not a Discord member, so control checks do not apply;
			// the request still goes through the same engine and queue.
			music.playAsOperator(guildId, query);
			queryField.clear();
			playbackStatus.setText(i18n.get("music.searching"));
		});
	}

	@FXML
	private void removeSelected() {
		QueueRow row = queueTable.getSelectionModel().getSelectedItem();
		selection.selectedGuildId().ifPresent(guildId -> {
			if (row != null && music.removeFromQueue(guildId, row.index())) {
				refresh();
			}
		});
	}

	@FXML
	private void clearQueue() {
		selection.selectedGuildId().ifPresent(guildId -> {
			music.clearQueue(guildId);
			refresh();
		});
	}

	// ------------------------------------------------------------------ engine card

	@FXML
	private void updateEngine() {
		engineResult.setText(i18n.get("music.engine.updating"));
		Thread.ofVirtual().name("engine-update").start(() -> {
			EngineUpdater.UpdateResult ytDlp = updater.updateYtDlp();
			EngineUpdater.UpdateResult deno = updater.updateDeno();
			javafx.application.Platform.runLater(() -> {
				engineResult.setText(i18n.get("music.engine.updateResult", ytDlp.status().name(),
					deno.status().name()));
				refreshEngineCard();
			});
		});
	}

	@FXML
	private void openEnginesFolder() {
		openFolder(installer.enginesDir());
	}

	@FXML
	private void openMusicFolder() {
		String configured = settings.get(AppSettingKey.MUSIC_FOLDER);
		openFolder(configured == null || configured.isBlank()
			? AppPaths.dataDir().resolve("music")
			: Path.of(configured));
	}

	private void refreshEngineCard() {
		EngineStatus status = router.status();
		engineStatus.setText(i18n.get(status.summaryKey()));
		engineVersions.setText(i18n.get("music.engine.versions",
			status.ytDlpVersion().isBlank() ? "-" : status.ytDlpVersion(),
			status.denoVersion().isBlank() ? "-" : status.denoVersion()));
		updateEngineButton.setDisable(!installer.hasBundledEngines() && !status.ytDlpAvailable());
	}

	// ------------------------------------------------------------------ account card

	@FXML
	private void linkAccount() {
		YoutubeOauthController dialog = (YoutubeOauthController) viewLoader
			.loadWithController("/fxml/dialogs/youtube-oauth.fxml").controller();
		java.util.Optional.ofNullable(nowPlayingValue.getScene())
			.ifPresent(scene -> dialog.show(scene.getWindow()));
		refreshAccountCard();
	}

	@FXML
	private void unlinkAccount() {
		if (!UiUtils.confirm(i18n.get("music.account.unlink.confirm.title"),
			i18n.get("music.account.unlink.confirm.header"), i18n.get("music.account.unlink.confirm.body"))) {
			return;
		}
		oauth.disconnect();
		refreshAccountCard();
	}

	private void refreshAccountCard() {
		accountStatus.setText(i18n.get(oauth.isLinked() ? "music.account.linked" : "music.account.notLinked"));
		linkAccountButton.setDisable(!oauth.isAvailable());
		unlinkAccountButton.setDisable(!oauth.isLinked());
	}

	// ------------------------------------------------------------------ refresh

	private void refresh() {
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			var playing = music.nowPlaying(guildId);
			boolean paused = music.isPaused(guildId);
			playing.ifPresentOrElse(track -> {
				nowPlayingValue.setText(i18n.get("music.nowPlaying.value", track.getInfo().title,
					track.getInfo().author));
				trackTime.setText(MusicEmbeds.formatDuration(track.getPosition()) + " / "
					+ (track.getInfo().isStream ? i18n.get("music.embed.live")
						: MusicEmbeds.formatDuration(track.getDuration())));
				double progress = track.getInfo().length <= 0 ? 0
					: (double) track.getPosition() / track.getInfo().length;
				trackProgress.setProgress(Math.min(1, Math.max(0, progress)));
				loadArtwork(track.getInfo().artworkUrl);
			}, () -> {
				nowPlayingValue.setText(i18n.get("music.nothingPlaying"));
				trackTime.setText("");
				trackProgress.setProgress(0);
				artwork.setImage(null);
			});

			pauseButton.setText(i18n.get(paused ? "music.resume" : "music.pause"));
			RepeatMode repeat = music.repeat(guildId);
			loopButton.setText(i18n.get(repeat.labelKey()));
			shuffleButton.setText(i18n.get(music.shuffle(guildId) ? "music.shuffleOn" : "music.shuffleOff"));
			if (!volumeSlider.isValueChanging()) {
				volumeSlider.setValue(music.volume(guildId));
			}
			volumeValue.setText(music.volume(guildId) + "%");

			var queue = music.queue(guildId);
			var rows = new java.util.ArrayList<QueueRow>();
			for (int index = 0; index < queue.size(); index++) {
				AudioTrack track = queue.get(index);
				rows.add(new QueueRow(index + 1, track.getInfo().title,
					MusicEmbeds.formatDuration(track.getDuration())));
			}
			queueTable.setItems(FXCollections.observableArrayList(rows));
			queueTitle.setText(i18n.get("music.queue.count", rows.size()));
			connectButton.setDisable(music.isConnected(guildId));
			disconnectButton.setDisable(!music.isConnected(guildId));
		}, () -> {
			nowPlayingValue.setText(i18n.get("dashboard.noGuild"));
			queueTable.setItems(FXCollections.observableArrayList());
		});
	}

	private void loadArtwork(String url) {
		if (url == null || url.isBlank()) {
			artwork.setImage(null);
			return;
		}
		if (artwork.getImage() != null && url.equals(artwork.getProperties().get("url"))) {
			return;
		}
		try {
			Image image = new Image(url, 96, 96, true, true, true);
			artwork.setImage(image);
			artwork.getProperties().put("url", url);
		} catch (Exception e) {
			log.debug("Could not load artwork {}", url);
			artwork.setImage(null);
		}
	}

	private void openFolder(Path folder) {
		try {
			java.nio.file.Files.createDirectories(folder);
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				Desktop.getDesktop().open(folder.toFile());
				return;
			}
		} catch (Exception e) {
			log.debug("Could not open {}", folder);
		}
		UiUtils.copyToClipboard(folder.toString());
	}

	/** Kept for the FXML loader: a File is what the desktop API expects. */
	static File asFile(Path path) {
		return path.toFile();
	}
}
