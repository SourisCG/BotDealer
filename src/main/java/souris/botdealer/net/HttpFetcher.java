/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.net;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Minimal HTTP access for the engine updater.
 *
 * <p>An interface so the update flow (checksum verification, atomic swap, rollback) can
 * be tested without touching the network — the part that must not go wrong is the
 * replacing, not the downloading.</p>
 */
public interface HttpFetcher {

	/** Downloads a small text resource, e.g. a release manifest or a checksum listing. */
	String getText(String url) throws IOException;

	/** Downloads a file to the given path, returning the number of bytes written. */
	long download(String url, Path target) throws IOException;
}
