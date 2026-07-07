package souris.jarvisdealer.ui;

import javafx.fxml.FXML;

public class FirstConfigurationController {
    @FXML
    private void openDiscordPortal() {
        // Open the Discord portal in the default web browser
        String discordPortalUrl = "https://discord.com/developers/applications";
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(discordPortalUrl));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
