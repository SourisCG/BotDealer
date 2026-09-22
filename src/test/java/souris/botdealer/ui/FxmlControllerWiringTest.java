/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that every FXML file can actually be wired to its controller.
 *
 * <p>FXMLLoader resolves {@code fx:id} and {@code onAction="#method"} at load time, so a
 * typo or a renamed method only shows up when the screen is opened — the compiler cannot
 * see it. This test reflects over the compiled controllers and verifies both, which is
 * exactly the failure it was written after hitting twice.</p>
 */
class FxmlControllerWiringTest {

	private static final Pattern CONTROLLER = Pattern.compile("fx:controller=\"([^\"]+)\"");
	private static final Pattern ID = Pattern.compile("fx:id=\"([A-Za-z0-9_]+)\"");
	private static final Pattern ACTION = Pattern.compile("onAction=\"#([A-Za-z0-9_]+)\"");
	private static final Pattern CHANGE_LISTENER = Pattern.compile("on[A-Za-z]+Change=\"#([A-Za-z0-9_]+)\"");

	@Test
	void everyFxmlIdAndHandlerExistsInItsController() throws Exception {
		TreeSet<String> problems = new TreeSet<>();
		for (Path fxml : fxmlFiles()) {
			String content = Files.readString(fxml);
			Matcher controllerMatcher = CONTROLLER.matcher(content);
			if (!controllerMatcher.find()) {
				problems.add(fxml.getFileName() + ": no fx:controller");
				continue;
			}
			Class<?> controller = Class.forName(controllerMatcher.group(1));
			List<String> fields = declaredFields(controller);
			List<String> methods = declaredMethods(controller);

			check(content, ID, fields, methods, fxml, "fx:id", problems, false);
			check(content, ACTION, fields, methods, fxml, "onAction", problems, true);
			check(content, CHANGE_LISTENER, fields, methods, fxml, "change listener", problems, true);
		}
		assertTrue(problems.isEmpty(), "FXML wiring problems: " + problems);
	}

	@Test
	void theScannerSeesEveryFxmlFile() throws IOException {
		assertFalse(fxmlFiles().isEmpty(), "no FXML files found; the scanner is broken");
	}

	private static void check(String content, Pattern pattern, List<String> fields, List<String> methods,
			Path fxml, String kind, TreeSet<String> problems, boolean mustBeMethod) {
		Matcher matcher = pattern.matcher(content);
		while (matcher.find()) {
			String name = matcher.group(1);
			boolean present = mustBeMethod ? methods.contains(name) : fields.contains(name);
			if (!present) {
				problems.add(fxml.getFileName() + ": " + kind + " '" + name + "' not found in its controller");
			}
		}
	}

	private static List<Path> fxmlFiles() throws IOException {
		try (Stream<Path> files = Files.walk(Path.of("src/main/resources/fxml"))) {
			return files.filter(path -> path.toString().endsWith(".fxml")).toList();
		}
	}

	private static List<String> declaredFields(Class<?> type) {
		List<String> names = new ArrayList<>();
		for (Field field : type.getDeclaredFields()) {
			names.add(field.getName());
		}
		return names;
	}

	private static List<String> declaredMethods(Class<?> type) {
		List<String> names = new ArrayList<>();
		for (Method method : type.getDeclaredMethods()) {
			names.add(method.getName());
		}
		return names;
	}
}
