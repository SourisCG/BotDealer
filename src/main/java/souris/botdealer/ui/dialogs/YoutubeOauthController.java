/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.dialogs;

import java.util.Optional;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.music.engine.YouTubeOauthService;
import souris.botdealer.ui.UiUtils;

/**
 * YouTube account linking, using Google's device-code flow.
 *
 * <p>Shows the code and the page to type it on, then polls Google on a background thread
 * until the user finishes or the code expires. The dialog never sees a password: only the
 * refresh token Google hands back, which goes into the OS keychain.</p>
 *
 * <p>The library warns that linking should use a throwaway account, so the dialog says so
 * plainly instead of hiding it in the documentation.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class YoutubeOauthController {

	private static final Logger log = LoggerFactory.getLogger(YoutubeOauthController.class);

	@FXML
	private VBox root;
	@FXML
	private Label headingLabel;
	@FXML
	private Label introLabel;
	@FXML
	private Label urlLabel;
	@FXML
	private Label urlValue;
	@FXML
	private Label codeLabel;
	@FXML
	private Label codeValue;
	@FXML
	private Button copyButton;
	@FXML
	private ProgressBar pollProgress;
	@FXML
	private Label statusLabel;
	@FXML
	private Label warningLabel;

	private final I18nService i18n;
	private final YouTubeOauthService oauth;

	private Dialog<Boolean> dialog;
	private Timeline poller;
	private YouTubeOauthService.DeviceCode deviceCode;

	public YoutubeOauthController(I18nService i18n, YouTubeOauthService oauth) {
		this.i18n = i18n;
		this.oauth = oauth;
	}

	/** @return true when an account was linked */
	public boolean show(Window owner) {
		buildDialog(owner);
		Boolean linked = dialog.showAndWait().orElse(false);
		stopPolling();
		return Boolean.TRUE.equals(linked);
	}

	private void buildDialog(Window owner) {
		dialog = new Dialog<>();
		dialog.setTitle(i18n.get("music.oauth.title"));
		dialog.initModality(Modality.WINDOW_MODAL);
		if (owner != null) {
			dialog.initOwner(owner);
		}
		dialog.getDialogPane().setContent(root);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);

		headingLabel.setText(i18n.get("music.oauth.heading"));
		introLabel.setText(i18n.get("music.oauth.intro"));
		urlLabel.setText(i18n.get("music.oauth.urlLabel"));
		codeLabel.setText(i18n.get("music.oauth.codeLabel"));
		copyButton.setText(i18n.get("music.oauth.copy"));
		warningLabel.setText(i18n.get("music.oauth.warning"));

		Optional<YouTubeOauthService.DeviceCode> requested = oauth.requestDeviceCode();
		if (requested.isEmpty()) {
			statusLabel.setText(i18n.get("music.oauth.unavailable"));
			pollProgress.setVisible(false);
			return;
		}
		deviceCode = requested.get();
		urlValue.setText(deviceCode.verificationUrl());
		codeValue.setText(deviceCode.userCode());
		statusLabel.setText(i18n.get("music.oauth.waiting"));
		startPolling();
	}

	@FXML
	private void copyCode() {
		if (deviceCode != null) {
			UiUtils.copyToClipboard(deviceCode.userCode());
			statusLabel.setText(i18n.get("music.oauth.copied"));
		}
	}

	/** Polls Google every interval; runs on a daemon thread, updates the UI on the FX thread. */
	private void startPolling() {
		poller = new Timeline(new KeyFrame(Duration.seconds(deviceCode.intervalSeconds()), event -> pollOnce()));
		poller.setCycleCount(Timeline.INDEFINITE);
		poller.play();
	}

	private void pollOnce() {
		YouTubeOauthService.DeviceCode code = deviceCode;
		if (code == null) {
			return;
		}
		Thread.ofVirtual().name("youtube-oauth-poll").start(() -> {
			YouTubeOauthService.PollResult result = oauth.poll(code);
			Platform.runLater(() -> applyPoll(result));
		});
	}

	private void applyPoll(YouTubeOauthService.PollResult result) {
		switch (result.status()) {
			case PENDING -> statusLabel.setText(i18n.get("music.oauth.waiting"));
			case READY -> {
				oauth.link(result.refreshToken());
				stopPolling();
				statusLabel.setText(i18n.get("music.oauth.linked"));
				dialog.setResult(true);
				dialog.close();
			}
			case DENIED -> {
				stopPolling();
				statusLabel.setText(i18n.get("music.oauth.denied"));
			}
			case EXPIRED -> {
				stopPolling();
				statusLabel.setText(i18n.get("music.oauth.expired"));
			}
			case ERROR -> {
				stopPolling();
				statusLabel.setText(i18n.get("music.oauth.error"));
			}
		}
	}

	private void stopPolling() {
		if (poller != null) {
			poller.stop();
			poller = null;
		}
		deviceCode = null;
		log.debug("YouTube linking dialog closed");
	}
}
