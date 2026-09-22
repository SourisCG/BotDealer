/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs an external command with an argument <b>list</b> — never a shell string — and
 * bounded output.
 *
 * <p>Used for yt-dlp and Deno. Security rules baked in here: no shell interpolation,
 * a hard timeout that destroys the process, a cap on how many output lines are kept,
 * and stderr captured separately so callers can log it without leaking user data into
 * the console.</p>
 */
public final class ProcessRunner {

	private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

	private static final int MAX_LINES = 500;

	private ProcessRunner() {
	}

	/** Outcome of a finished (or killed) process. */
	public record Result(int exitCode, List<String> stdout, String stderr, boolean timedOut) {

		public boolean ok() {
			return !timedOut && exitCode == 0;
		}

		/** First non-blank stdout line, which is what yt-dlp prints for --get-url. */
		public String firstLine() {
			return stdout.stream().filter(line -> !line.isBlank()).findFirst().orElse("");
		}
	}

	public static Result run(List<String> command, Duration timeout) {
		return run(command, timeout, Map.of());
	}

	public static Result run(List<String> command, Duration timeout, Map<String, String> extraEnvironment) {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().putAll(extraEnvironment);
		builder.redirectErrorStream(false);

		Process process = null;
		try {
			process = builder.start();
			List<String> stdout = readLines(process.getInputStream(), MAX_LINES);
			String stderr = String.join("\n", readLines(process.getErrorStream(), MAX_LINES));
			boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!finished) {
				process.destroyForcibly();
				process.waitFor(5, TimeUnit.SECONDS);
				log.warn("Command timed out after {}s: {}", timeout.toSeconds(), command.get(0));
				return new Result(-1, stdout, stderr, true);
			}
			return new Result(process.exitValue(), stdout, stderr, false);
		} catch (IOException e) {
			return new Result(-1, List.of(), e.getClass().getSimpleName() + ": " + e.getMessage(), false);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (process != null) {
				process.destroyForcibly();
			}
			return new Result(-1, List.of(), "interrupted", false);
		}
	}

	private static List<String> readLines(java.io.InputStream stream, int maxLines) {
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (lines.size() < maxLines) {
					lines.add(line);
				}
			}
		} catch (IOException e) {
			log.debug("Could not read process output: {}", e.toString());
		}
		return lines;
	}
}
