package souris.jarvisdealer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.shape.Rectangle;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class StartController {
	@FXML
	private Label welcomeLabel;

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
	private void handleStartButton() {
	}
}
