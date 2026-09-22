/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Access to the assets the installers ship next to the application.
 *
 * <p>jpackage lays the input directory out as {@code <app>/lib/app}, and sets
 * {@code jpackage.app-path} to {@code <app>/bin/<launcher>}. That directory is
 * <b>read-only</b> once installed (under {@code /opt} or Program Files), while the
 * engines need to be self-updatable, so bundled content is <b>seeded</b> — copied once —
 * into the writable data folder and used from there.</p>
 *
 * <p>Development runs have no app image; {@code target/app} is checked instead so
 * {@code mvn -Ppackage} output can be tested locally.</p>
 */
@Component
public class BundledAssets {

	private static final Logger log = LoggerFactory.getLogger(BundledAssets.class);

	/** Root of the bundled content, or empty when running from sources. */
	public Optional<Path> root() {
		String appPath = System.getProperty("jpackage.app-path");
		if (appPath != null && !appPath.isBlank()) {
			Path launcher = Path.of(appPath);
			Path appDir = launcher.getParent() == null ? null : launcher.getParent().getParent();
			if (appDir != null) {
				Path candidate = appDir.resolve("lib").resolve("app");
				if (Files.isDirectory(candidate)) {
					return Optional.of(candidate);
				}
			}
		}
		Path dev = Path.of("target", "app");
		return Files.isDirectory(dev) ? Optional.of(dev) : Optional.empty();
	}

	/** A named folder inside the bundled content, e.g. {@code engines} or {@code voices}. */
	public Optional<Path> directory(String name) {
		return root().map(base -> base.resolve(name)).filter(Files::isDirectory);
	}

	/**
	 * Copies every file of a bundled folder into {@code target}, skipping anything that
	 * already exists so an updated file is never clobbered by the shipped one.
	 *
	 * @return the relative paths that were copied
	 */
	public List<String> seed(String name, Path target) {
		Optional<Path> source = directory(name);
		if (source.isEmpty()) {
			log.debug("No bundled '{}' folder found", name);
			return List.of();
		}
		List<String> copied = new ArrayList<>();
		try {
			Files.createDirectories(target);
			try (Stream<Path> files = Files.walk(source.get())) {
				for (Path file : files.filter(Files::isRegularFile).toList()) {
					String relative = source.get().relativize(file).toString();
					Path destination = target.resolve(relative);
					if (Files.exists(destination)) {
						continue;
					}
					Files.createDirectories(destination.getParent());
					Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
					makeExecutableIfBinary(destination);
					copied.add(relative);
				}
			}
			if (!copied.isEmpty()) {
				log.info("Seeded {} bundled file(s) from '{}' into {}", copied.size(), name, target);
			}
		} catch (IOException e) {
			log.warn("Could not seed the bundled '{}' folder: {}", name, e.toString());
		}
		return copied;
	}

	/** Grants the executable bit for files that have no extension (binaries on POSIX). */
	private static void makeExecutableIfBinary(Path path) {
		String fileName = path.getFileName().toString();
		if (fileName.contains(".")) {
			return;
		}
		path.toFile().setExecutable(true, true);
	}

	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
