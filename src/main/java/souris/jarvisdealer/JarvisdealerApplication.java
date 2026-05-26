package souris.jarvisdealer;

import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import souris.jarvisdealer.ui.MainApp;

@SpringBootApplication
public class JarvisdealerApplication {

	public static void main(String[] args) {	
		SpringApplication.run(JarvisdealerApplication.class, args);
		Application.launch(MainApp.class, args);
	}
}
