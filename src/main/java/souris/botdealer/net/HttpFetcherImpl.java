/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.springframework.stereotype.Component;

/**
 * {@link HttpFetcher} backed by the JDK HTTP client. Follows redirects (GitHub release
 * assets redirect to a CDN) and refuses anything that is not https.
 */
@Component
public class HttpFetcherImpl implements HttpFetcher {

	private static final Duration TIMEOUT = Duration.ofMinutes(5);

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	@Override
	public String getText(String url) throws IOException {
		HttpRequest request = request(url).GET().build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				throw new IOException("HTTP " + response.statusCode() + " for " + url);
			}
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while fetching " + url, e);
		}
	}

	@Override
	public long download(String url, Path target) throws IOException {
		HttpRequest request = request(url).GET().build();
		Files.createDirectories(target.getParent());
		Path temp = target.resolveSibling(target.getFileName() + ".part");
		try {
			HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp));
			if (response.statusCode() / 100 != 2) {
				Files.deleteIfExists(temp);
				throw new IOException("HTTP " + response.statusCode() + " for " + url);
			}
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			return Files.size(target);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while downloading " + url, e);
		}
	}

	private static HttpRequest.Builder request(String url) throws IOException {
		if (!url.startsWith("https://")) {
			throw new IOException("Refusing a non-https download: " + url);
		}
		return HttpRequest.newBuilder(URI.create(url))
			.timeout(TIMEOUT)
			.header("User-Agent", "BotDealer (https://github.com/SourisCG/BotDealer)");
	}
}
