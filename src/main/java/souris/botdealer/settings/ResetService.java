/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import souris.botdealer.config.AppPaths;
import souris.botdealer.security.SecretService;

/**
 * "Reset the app as if it were the first run".
 *
 * <p>Deletes every row of every entity (discovered from the JPA metamodel, so new
 * entities are covered automatically), removes all stored credentials and deletes
 * downloaded assets (voices, engine binaries, cache). The schema itself is left in
 * place and Hibernate keeps it up to date, which avoids the stale-metamodel problems
 * of dropping tables while the context is alive.</p>
 *
 * <p>Referential integrity is disabled during the wipe because entity deletion order
 * is arbitrary; the setting is restored in a {@code finally} block.</p>
 */
@Service
public class ResetService {

	private static final Logger log = LoggerFactory.getLogger(ResetService.class);

	private static final String[] DOWNLOAD_DIRECTORIES = {"voices", "engines", "cache"};

	private final SecretService secrets;

	@PersistenceContext
	private EntityManager entityManager;

	public ResetService(SecretService secrets) {
		this.secrets = secrets;
	}

	/** Wipes settings, wallets, events, credentials and downloaded assets. */
	@Transactional
	public void resetEverything() {
		log.info("Application reset requested: removing all data");
		wipeDatabase();
		secrets.removeAll();
		removeDownloadedAssets();
		log.info("Application reset finished; BotDealer will behave like a first run");
	}

	/** Deletes only the credentials, keeping the economy data ("forget token"). */
	public void forgetCredentials() {
		secrets.removeAll();
		log.info("Stored credentials removed");
	}

	/**
	 * Relaunches the application when running from a jpackage image, otherwise just
	 * quits: in a dev run the user restarts it with Maven.
	 */
	public void restartApplication() {
		String jpackagePath = System.getProperty("jpackage.app-path");
		if (jpackagePath != null && !jpackagePath.isBlank()) {
			try {
				new ProcessBuilder(jpackagePath).start();
				log.info("Relaunching BotDealer");
			} catch (IOException e) {
				log.warn("Could not relaunch the application automatically", e);
			}
		} else {
			log.info("Not running from a packaged image; please start BotDealer again");
		}
		Platform.exit();
	}

	private void wipeDatabase() {
		boolean integrityDisabled = false;
		try {
			entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
			integrityDisabled = true;
			for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
				entityManager.createQuery("delete from " + entity.getName()).executeUpdate();
			}
			entityManager.clear();
		} finally {
			if (integrityDisabled) {
				entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
			}
		}
	}

	private void removeDownloadedAssets() {
		for (String directory : DOWNLOAD_DIRECTORIES) {
			Path path = AppPaths.dataDir().resolve(directory);
			if (!Files.isDirectory(path)) {
				continue;
			}
			try (Stream<Path> walk = Files.walk(path)) {
				walk.sorted(Comparator.reverseOrder())
					.filter(candidate -> !candidate.equals(path))
					.forEach(ResetService::deleteQuietly);
			} catch (IOException e) {
				log.warn("Could not clean the '{}' folder during reset", directory, e);
			}
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.debug("Could not delete {}", path.getFileName());
		}
	}
}
