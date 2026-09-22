/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Resolves and creates the per-user application data directory.
 *
 * <ul>
 *   <li>Windows: {@code %APPDATA%\BotDealer}</li>
 *   <li>Linux/macOS: {@code ~/.local/share/BotDealer} (XDG), falling back to {@code ~/.botdealer}</li>
 * </ul>
 *
 * <p>The resolved directory is exposed as the {@code botdealer.data.dir} system property
 * BEFORE Spring boots, so {@code application.properties} can reference it for the H2 file
 * database, logs and any other file storage. Never use a path relative to the working
 * directory: installers change the CWD.</p>
 */
public final class AppPaths {

	public static final String DATA_DIR_PROPERTY = "botdealer.data.dir";
	public static final String APP_DIR_NAME = "BotDealer";

	private static volatile Path dataDir;

	private AppPaths() {
	}

	/**
	 * Resolves the data directory, creates it (including db/logs subdirs) and publishes
	 * the system property. Idempotent and thread-safe.
	 */
	public static synchronized Path init() {
		if (dataDir != null) {
			return dataDir;
		}
		dataDir = resolve();
		try {
			Files.createDirectories(dataDir.resolve("db"));
			Files.createDirectories(dataDir.resolve("logs"));
			Files.createDirectories(dataDir.resolve("config"));
		} catch (Exception e) {
			throw new IllegalStateException("Cannot create BotDealer data directory: " + dataDir, e);
		}
		System.setProperty(DATA_DIR_PROPERTY, dataDir.toString());
		return dataDir;
	}

	public static Path dataDir() {
		Path dir = dataDir;
		return dir != null ? dir : init();
	}

	public static Path dbDir() {
		return dataDir().resolve("db");
	}

	public static Path logsDir() {
		return dataDir().resolve("logs");
	}

	public static Path configDir() {
		return dataDir().resolve("config");
	}

	private static Path resolve() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String home = System.getProperty("user.home");
		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			if (appData != null && !appData.isBlank()) {
				return Paths.get(appData, APP_DIR_NAME);
			}
			return Paths.get(home, "AppData", "Roaming", APP_DIR_NAME);
		}
		String xdg = System.getenv("XDG_DATA_HOME");
		if (xdg != null && !xdg.isBlank()) {
			return Paths.get(xdg, APP_DIR_NAME);
		}
		Path xdgDefault = Paths.get(home, ".local", "share", APP_DIR_NAME);
		if (Files.isDirectory(xdgDefault.getParent())) {
			return xdgDefault;
		}
		return Paths.get(home, ".botdealer");
	}
}
