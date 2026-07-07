package souris.jarvisdealer.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.util.Duration;
import java.io.IOException;

public class StartController {
	@FXML
	private Label welcomeLabel;
	@FXML
	private ProgressBar progressBar;
	
	private FXMLLoader loader;

	public void initialize() {
		welcomeLabel.setOpacity(0);

		welcomeLabel.boundsInLocalProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue.getWidth() > 0 && welcomeLabel.getClip() == null) {
				revealAnimation(newValue.getWidth(), newValue.getHeight(), welcomeLabel);
			}
		});
	}

	private void revealAnimation(double labelWidth, double labelHeight, Label animatedLabel) {
		Rectangle mask = new Rectangle(labelWidth, 0);
		animatedLabel.setClip(mask);

		Timeline timeline = new Timeline();

		KeyValue kvHeight = new KeyValue(mask.heightProperty(), labelHeight, Interpolator.LINEAR);
		KeyValue kvOpacity = new KeyValue(animatedLabel.opacityProperty(), 1, Interpolator.LINEAR);

		KeyFrame kf = new KeyFrame(Duration.seconds(1), kvHeight, kvOpacity);
		timeline.getKeyFrames().add(kf);

		timeline.play();
	}

	@FXML
	private void start() {
		progressBar.setVisible(true);
		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				for (int i = 0; i <= 100; i++) {
					Thread.sleep(50);
					updateProgress(i, 100);
				}
				return null;
			}
		};
		progressBar.progressProperty().bind(task.progressProperty());
		new Thread(task).start();
		loader = new FXMLLoader(getClass().getResource("/fxml/firstConfiguration.fxml"));
		try {
			Stage stage = (Stage) progressBar.getScene().getWindow();
			stage.setScene(new Scene(loader.load(), 800, 600));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
