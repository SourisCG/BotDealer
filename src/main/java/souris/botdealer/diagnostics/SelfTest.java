/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import souris.botdealer.BotDealerApplication;
import souris.botdealer.config.AppPaths;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.security.SecretService;
import souris.botdealer.security.SecretStore;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

/**
 * Headless verification of a packaged build: {@code BotDealer --self-test}.
 *
 * <p>CI runs this on every installer/app-image, because the failures that matter for
 * packaging (a missing jlink module, a missing native library, an unwritable data
 * folder) only show up at runtime — and a GUI application cannot be launched on a
 * build runner. It boots Spring without JavaFX, runs the checks, prints a report and
 * exits with a status code.</p>
 *
 * <p>Fatal checks decide the exit code; warnings are reported but tolerated because
 * music and TTS are optional at runtime.</p>
 */
public final class SelfTest {

	private static final Logger log = LoggerFactory.getLogger(SelfTest.class);

	private static final String FLAG = "--self-test";

	private final List<String> failures = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();

	private SelfTest() {
	}

	public static boolean isRequested(String[] args) {
		if (args == null) {
			return false;
		}
		for (String arg : args) {
			if (FLAG.equals(arg)) {
				return true;
			}
		}
		return false;
	}

	/** @return 0 when every fatal check passed, 1 otherwise */
	public static int run(String[] args) {
		return new SelfTest().execute();
	}

	private int execute() {
		System.out.println("BotDealer self-test");
		System.out.println("===================");

		checkDataDirectory();

		ConfigurableApplicationContext context = null;
		try {
			context = new SpringApplicationBuilder(BotDealerApplication.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off", "logging.level.root=WARN")
				.run();
			checkDatabase(context);
			checkI18n(context);
			checkCredentialStore(context);
			checkBundledAssets();
		} catch (Exception e) {
			fail("Spring context failed to start: " + rootCause(e));
		} finally {
			if (context != null) {
				context.close();
			}
		}

		checkNativeLibraries();

		return report();
	}

	// ------------------------------------------------------------------ checks

	private void checkDataDirectory() {
		Path dataDir = AppPaths.dataDir();
		try {
			Files.createDirectories(dataDir);
			Path probe = Files.createTempFile(dataDir, "selftest", ".tmp");
			Files.deleteIfExists(probe);
			pass("Data folder is writable: " + dataDir);
		} catch (Exception e) {
			fail("Data folder is not writable (" + dataDir + "): " + rootCause(e));
		}
	}

	private void checkDatabase(ConfigurableApplicationContext context) {
		try {
			AppSettingsService settings = context.getBean(AppSettingsService.class);
			settings.snapshot();
			pass("Database reachable, settings readable (engine="
				+ settings.get(AppSettingKey.MUSIC_ENGINE) + ")");
		} catch (Exception e) {
			fail("Database check failed: " + rootCause(e));
		}
	}

	private void checkI18n(ConfigurableApplicationContext context) {
		try {
			I18nService i18n = context.getBean(I18nService.class);
			String title = i18n.get("app.title");
			if (title == null || title.isBlank() || title.startsWith("!")) {
				fail("i18n bundle is missing keys (app.title='" + title + "')");
				return;
			}
			pass("Translations loaded (" + i18n.getLocale() + ")");
		} catch (Exception e) {
			fail("i18n check failed: " + rootCause(e));
		}
	}

	private void checkCredentialStore(ConfigurableApplicationContext context) {
		try {
			SecretService secrets = context.getBean(SecretService.class);
			SecretStore store = secrets.store();
			if (store.isAvailable()) {
				pass("Credential storage ready: " + store.displayName() + " (" + store.id() + ")");
			} else {
				warn("Credential storage unavailable, encrypted file fallback in use: "
					+ store.unavailableReason());
			}
		} catch (Exception e) {
			fail("Credential store check failed: " + rootCause(e));
		}
	}

	private void checkBundledAssets() {
		Path voices = AppPaths.dataDir().resolve("voices");
		Path engines = AppPaths.dataDir().resolve("engines");
		reportAsset("voices", voices);
		reportAsset("music engines", engines);
	}

	private void reportAsset(String label, Path directory) {
		if (Files.isDirectory(directory) && directory.toFile().list() != null
				&& directory.toFile().list().length > 0) {
			pass("Bundled " + label + " present: " + directory);
		} else {
			warn("No bundled " + label + " found at " + directory
				+ " (downloadable from Settings when that phase lands)");
		}
	}

	/**
	 * Native libraries are the classic packaging casualty. None of them are fatal: the
	 * app degrades gracefully (voice without DAVE, TTS disabled), so they are warnings.
	 */
	private void checkNativeLibraries() {
		probeNative("JDAVE (Discord voice encryption)", () -> {
			Class<?> factory = Class.forName("club.minnced.discord.jdave.interop.JDaveSessionFactory");
			factory.getDeclaredConstructor().newInstance();
		});
		probeNative("Opus codec", () -> {
			Class<?> library = Class.forName("club.minnced.opus.util.OpusLibrary");
			Object supported = library.getMethod("isSupportedPlatform").invoke(null);
			if (!Boolean.TRUE.equals(supported)) {
				throw new IllegalStateException("platform not supported by the bundled natives");
			}
			library.getMethod("loadFromJar").invoke(null);
		});
		probeNative("Piper (offline TTS)", () -> {
			Class<?> piper = Class.forName("io.github.jvoiceproject.piperjni.PiperJNI");
			Object instance = piper.getDeclaredConstructor().newInstance();
			try {
				piper.getMethod("initialize").invoke(instance);
			} finally {
				piper.getMethod("close").invoke(instance);
			}
		});
	}

	private void probeNative(String label, NativeProbe probe) {
		try {
			probe.run();
			pass("Native library loads: " + label);
		} catch (Throwable t) {
			warn("Native library not usable (" + label + "): " + rootCause(t));
		}
	}

	// ------------------------------------------------------------------ report

	private int report() {
		System.out.println();
		for (String warning : warnings) {
			System.out.println("  WARN  " + warning);
		}
		for (String failure : failures) {
			System.out.println("  FAIL  " + failure);
		}
		System.out.println();
		if (failures.isEmpty()) {
			System.out.println("Self-test PASSED (" + warnings.size() + " warning(s))");
			return 0;
		}
		System.out.println("Self-test FAILED (" + failures.size() + " failure(s), "
			+ warnings.size() + " warning(s))");
		return 1;
	}

	private void pass(String message) {
		System.out.println("  ok    " + message);
		log.debug("self-test ok: {}", message);
	}

	private void warn(String message) {
		warnings.add(message);
		System.out.println("  warn  " + message);
	}

	private void fail(String message) {
		failures.add(message);
		System.out.println("  FAIL  " + message);
	}

	private static Throwable rootCause(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return current;
	}

	@FunctionalInterface
	private interface NativeProbe {
		void run() throws Throwable;
	}
}
