/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.settings;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import souris.botdealer.domain.AppSetting;
import souris.botdealer.repository.AppSettingRepository;

/**
 * Typed access to the persisted application settings.
 *
 * <p>Reads fall back to {@link AppSettingKey#defaultValue()} when a row is missing or
 * holds an unparseable value, so a corrupted setting can never prevent startup. No
 * caching on purpose: settings are read rarely and staleness would be worse than a
 * primary-key lookup.</p>
 */
@Service
public class AppSettingsService {

	private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);

	private final AppSettingRepository repository;

	public AppSettingsService(AppSettingRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public String get(AppSettingKey key) {
		return repository.findById(key.key())
			.map(AppSetting::getValue)
			.filter(value -> value != null && !value.isBlank())
			.orElseGet(key::defaultValue);
	}

	@Transactional(readOnly = true)
	public Optional<String> find(AppSettingKey key) {
		return repository.findById(key.key())
			.map(AppSetting::getValue)
			.filter(value -> value != null && !value.isBlank());
	}

	@Transactional
	public void set(AppSettingKey key, String value) {
		String stored = value == null ? "" : value;
		AppSetting setting = repository.findById(key.key()).orElseGet(() -> new AppSetting(key.key(), stored));
		setting.setValue(stored);
		repository.save(setting);
	}

	@Transactional
	public void reset(AppSettingKey key) {
		repository.deleteById(key.key());
	}

	@Transactional(readOnly = true)
	public boolean getBoolean(AppSettingKey key) {
		return Boolean.parseBoolean(get(key).strip());
	}

	@Transactional(readOnly = true)
	public int getInt(AppSettingKey key) {
		return parseNumber(key, Integer::parseInt).intValue();
	}

	@Transactional(readOnly = true)
	public double getDouble(AppSettingKey key) {
		return parseNumber(key, Double::parseDouble).doubleValue();
	}

	@Transactional(readOnly = true)
	public BigDecimal getDecimal(AppSettingKey key) {
		try {
			return new BigDecimal(get(key).strip());
		} catch (NumberFormatException e) {
			log.warn("Setting '{}' is not a decimal; using the default '{}'", key.key(), key.defaultValue());
			return new BigDecimal(key.defaultValue());
		}
	}

	@Transactional(readOnly = true)
	public Locale getLanguage() {
		return "es".equalsIgnoreCase(get(AppSettingKey.LANGUAGE).strip()) ? Locale.of("es") : Locale.ENGLISH;
	}

	@Transactional
	public void setLanguage(Locale locale) {
		set(AppSettingKey.LANGUAGE, "es".equalsIgnoreCase(locale.getLanguage()) ? "es" : "en");
	}

	@Transactional(readOnly = true)
	public boolean isOnboardingComplete() {
		return getBoolean(AppSettingKey.ONBOARDING_COMPLETE);
	}

	@Transactional
	public void setOnboardingComplete(boolean complete) {
		set(AppSettingKey.ONBOARDING_COMPLETE, Boolean.toString(complete));
	}

	/** Snapshot of every setting (including defaults) for export, diagnostics and tests. */
	@Transactional(readOnly = true)
	public Map<AppSettingKey, String> snapshot() {
		Map<AppSettingKey, String> snapshot = new EnumMap<>(AppSettingKey.class);
		for (AppSettingKey key : AppSettingKey.values()) {
			snapshot.put(key, get(key));
		}
		return snapshot;
	}

	@Transactional
	public void replaceAll(Map<AppSettingKey, String> values) {
		values.forEach(this::set);
	}

	@Transactional
	public void deleteAll() {
		repository.deleteAllInBatch();
	}

	private Number parseNumber(AppSettingKey key, Function<String, Number> parser) {
		String raw = get(key).strip();
		try {
			return parser.apply(raw);
		} catch (NumberFormatException e) {
			log.warn("Setting '{}' is not a number ('{}'); using the default '{}'", key.key(), raw,
				key.defaultValue());
			return parser.apply(key.defaultValue());
		}
	}
}
