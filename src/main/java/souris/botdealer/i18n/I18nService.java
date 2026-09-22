/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.springframework.stereotype.Service;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Central internationalization service.
 *
 * <p>Supported UI languages: English and Spanish. The initial locale is detected from
 * the OS ({@link Locale#getDefault()}): Spanish when the OS language is {@code es},
 * English otherwise. The user can switch at runtime; JavaFX-bound components observe
 * {@link #localeProperty()}.</p>
 *
 * <p>Discord bot replies are localized through this service as well, using the same
 * bundles (per-guild overrides arrive in Phase 3).</p>
 */
@Service
public class I18nService {

	public static final Locale ENGLISH = Locale.ENGLISH;
	public static final Locale SPANISH = Locale.of("es");
	private static final String BUNDLE_BASE = "i18n/messages";

	private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(detectDefault());
	private final java.util.Map<String, ResourceBundle> bundles = new java.util.concurrent.ConcurrentHashMap<>();
	private volatile ResourceBundle bundle;

	public I18nService() {
		this.bundle = loadBundle(locale.get());
		this.locale.addListener((obs, oldValue, newValue) -> bundle = loadBundle(newValue));
	}

	public static Locale detectDefault() {
		Locale system = Locale.getDefault();
		if (system != null && "es".equalsIgnoreCase(system.getLanguage())) {
			return SPANISH;
		}
		return ENGLISH;
	}

	public ReadOnlyObjectProperty<Locale> localeProperty() {
		return locale;
	}

	public Locale getLocale() {
		return locale.get();
	}

	/** Switches the language on the FX thread so bound UI controls refresh safely. */
	public void setLocale(Locale newLocale) {
		Locale target = SPANISH.getLanguage().equals(newLocale.getLanguage()) ? SPANISH : ENGLISH;
		if (Platform.isFxApplicationThread()) {
			locale.set(target);
		} else {
			Platform.runLater(() -> locale.set(target));
		}
	}

	public ResourceBundle getBundle() {
		return bundle;
	}

	/**
	 * Looks a key up in a specific locale, for content that is not the UI language.
	 *
	 * <p>The Discord bot needs this: every guild can run in its own language while the
	 * desktop window stays in the operator's language.</p>
	 */
	public String get(Locale target, String key, Object... args) {
		ResourceBundle targetBundle = target == null
			? bundle
			: bundles.computeIfAbsent(target.getLanguage(), language -> loadBundle(Locale.of(language)));
		String pattern;
		try {
			pattern = targetBundle.getString(key);
		} catch (MissingResourceException e) {
			return "!" + key + "!";
		}
		if (args == null || args.length == 0) {
			return pattern;
		}
		return MessageFormat.format(pattern, args);
	}

	/** Looks up a key and formats it with {@link MessageFormat}; never throws. */
	public String get(String key, Object... args) {
		String pattern;
		try {
			pattern = bundle.getString(key);
		} catch (MissingResourceException e) {
			return "!" + key + "!";
		}
		if (args == null || args.length == 0) {
			return pattern;
		}
		return MessageFormat.format(pattern, args);
	}

	private static ResourceBundle loadBundle(Locale locale) {
		// Java 9+ reads .properties resource bundles as UTF-8 by default, so Spanish
		// accents need no custom ResourceBundle.Control.
		return ResourceBundle.getBundle(BUNDLE_BASE, locale);
	}
}
