/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.springframework.stereotype.Component;
import souris.botdealer.config.AppPaths;
import souris.botdealer.i18n.I18nService;

/**
 * Boot screen controller (Phase 0 placeholder).
 *
 * <p>Shows that Spring + JavaFX + i18n are wired together. Phase 1 replaces this
 * screen with the first-run setup wizard; the reveal animation and the design-system
 * style classes stay as the visual baseline.</p>
 */
@Component
public class StartController {

	@FXML
	private Label welcomeLabel;
	@FXML
	private Label infoLabel;
	@FXML
	private Button startButton;
	@FXML
	private ProgressBar progressBar;

	private final I18nService i18n;

	public StartController(I18nService i18n) {
		this.i18n = i18n;
	}

	public void initialize() {
		infoLabel.setText(i18n.get("app.boot.info",
			i18n.getLocale().getDisplayLanguage(i18n.getLocale()),
			AppPaths.dataDir().toString()));
		welcomeLabel.setOpacity(0);
		welcomeLabel.boundsInLocalProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue.getWidth() > 0 && welcomeLabel.getClip() == null) {
				revealAnimation(newValue.getWidth(), newValue.getHeight());
			}
		});
	}

	private void revealAnimation(double labelWidth, double labelHeight) {
		Rectangle mask = new Rectangle(labelWidth, 0);
		welcomeLabel.setClip(mask);

		Timeline timeline = new Timeline();
		KeyValue kvHeight = new KeyValue(mask.heightProperty(), labelHeight, Interpolator.LINEAR);
		KeyValue kvOpacity = new KeyValue(welcomeLabel.opacityProperty(), 1, Interpolator.LINEAR);
		timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), kvHeight, kvOpacity));
		timeline.play();
	}

	@FXML
	private void start() {
		startButton.setDisable(true);
		progressBar.setVisible(true);
		Task<Void> task = new Task<>() {
			@Override
			protected Void call() throws Exception {
				for (int i = 0; i <= 100; i++) {
					Thread.sleep(20);
					updateProgress(i, 100);
				}
				return null;
			}
		};
		progressBar.progressProperty().bind(task.progressProperty());
		Thread thread = new Thread(task, "boot-progress");
		thread.setDaemon(true);
		thread.start();
	}
}
