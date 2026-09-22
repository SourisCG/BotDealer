/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import java.awt.Desktop;
import java.net.URI;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small UI helpers shared by controllers: opening links, copying text and building
 * consistent dialogs. Keeping them here avoids repeating AWT/JavaFX glue everywhere.
 */
public final class UiUtils {

	private static final Logger log = LoggerFactory.getLogger(UiUtils.class);

	private UiUtils() {
	}

	/** Copies text to the system clipboard. */
	public static void copyToClipboard(String text) {
		ClipboardContent content = new ClipboardContent();
		content.putString(text);
		Clipboard.getSystemClipboard().setContent(content);
	}

	/**
	 * Opens a URL in the default browser when the desktop supports it, otherwise
	 * copies it to the clipboard so the user can paste it manually.
	 *
	 * @return true when the browser was launched
	 */
	public static boolean openUrl(String url) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(url));
				return true;
			}
		} catch (Exception e) {
			log.debug("Could not open the browser for {}", url);
		}
		copyToClipboard(url);
		return false;
	}

	/** Read-only dialog for longer help texts. */
	public static Alert helpDialog(String title, String body) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.CLOSE);
		alert.setTitle(title);
		alert.setHeaderText(null);
		TextArea area = new TextArea(body);
		area.setEditable(false);
		area.setWrapText(true);
		area.setPrefRowCount(10);
		alert.getDialogPane().setContent(area);
		return alert;
	}

	/** Yes/No confirmation; returns true only on an explicit confirm. */
	public static boolean confirm(String title, String header, String body) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION, body, ButtonType.CANCEL, ButtonType.OK);
		alert.setTitle(title);
		alert.setHeaderText(header);
		return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
	}
}
