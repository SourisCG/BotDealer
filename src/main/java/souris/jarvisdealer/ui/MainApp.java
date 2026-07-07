package souris.jarvisdealer.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application{
    @Override
    public void start(Stage arg0) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/start.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 800, 600);
        arg0.setScene(scene);
        arg0.setTitle("Jarvis Dealer");
        arg0.show();
    }
}
