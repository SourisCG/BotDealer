/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.economy;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import souris.botdealer.domain.GuildConfig;
import souris.botdealer.repository.GuildConfigRepository;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

/**
 * Single place where per-guild overrides are merged with the application defaults.
 *
 * <p>Every getter falls back to {@link AppSettingsService} when the guild has no value,
 * so callers never have to think about where a setting came from.</p>
 */
@Service
public class GuildConfigService {

	private static final Logger log = LoggerFactory.getLogger(GuildConfigService.class);

	private final GuildConfigRepository repository;
	private final AppSettingsService appSettings;

	public GuildConfigService(GuildConfigRepository repository, AppSettingsService appSettings) {
		this.repository = repository;
		this.appSettings = appSettings;
	}

	@Transactional(readOnly = true)
	public Optional<GuildConfig> find(long guildId) {
		return repository.findById(guildId);
	}

	/** Returns the stored overrides, creating an empty row on first use. */
	@Transactional
	public GuildConfig getOrCreate(long guildId) {
		return repository.findById(guildId).orElseGet(() -> {
			log.debug("Creating guild configuration for {}", guildId);
			return repository.save(new GuildConfig(guildId, null, null, null, null, null, null, null,
				null, null, null, null, null, java.time.Instant.now(), null));
		});
	}

	@Transactional
	public GuildConfig save(GuildConfig config) {
		config.setUpdatedAt(java.time.Instant.now());
		return repository.save(config);
	}

	// ------------------------------------------------------------ effective values

	@Transactional(readOnly = true)
	public Locale locale(long guildId) {
		return find(guildId)
			.map(GuildConfig::getLocale)
			.filter(value -> value != null && !value.isBlank())
			.map(value -> "es".equalsIgnoreCase(value) ? Locale.of("es") : Locale.ENGLISH)
			.orElseGet(appSettings::getLanguage);
	}

	@Transactional(readOnly = true)
	public String currencySingular(long guildId) {
		return override(guildId, GuildConfig::getCurrencySingular, AppSettingKey.CURRENCY_SINGULAR);
	}

	@Transactional(readOnly = true)
	public String currencyPlural(long guildId) {
		return override(guildId, GuildConfig::getCurrencyPlural, AppSettingKey.CURRENCY_PLURAL);
	}

	@Transactional(readOnly = true)
	public String currencySymbol(long guildId) {
		return override(guildId, GuildConfig::getCurrencySymbol, AppSettingKey.CURRENCY_SYMBOL);
	}

	@Transactional(readOnly = true)
	public BigDecimal startingBalance(long guildId) {
		return decimalOverride(guildId, GuildConfig::getStartingBalance, AppSettingKey.STARTING_BALANCE);
	}

	@Transactional(readOnly = true)
	public int dailyAmount(long guildId) {
		return intOverride(guildId, GuildConfig::getDailyAmount, AppSettingKey.DAILY_AMOUNT);
	}

	@Transactional(readOnly = true)
	public int dailyCooldownHours(long guildId) {
		return intOverride(guildId, GuildConfig::getDailyCooldownHours, AppSettingKey.DAILY_COOLDOWN_HOURS);
	}

	@Transactional(readOnly = true)
	public BigDecimal minBet(long guildId) {
		BigDecimal value = find(guildId).map(GuildConfig::getMinBet).orElse(null);
		return value != null ? value : BigDecimal.ONE;
	}

	/** Zero means "no upper limit". */
	@Transactional(readOnly = true)
	public BigDecimal maxBet(long guildId) {
		BigDecimal value = find(guildId).map(GuildConfig::getMaxBet).orElse(null);
		return value != null ? value : BigDecimal.ZERO;
	}

	@Transactional(readOnly = true)
	public BigDecimal rakePercent() {
		return appSettings.getDecimal(AppSettingKey.RAKE_PERCENT);
	}

	// ------------------------------------------------------------ helpers

	private String override(long guildId, java.util.function.Function<GuildConfig, String> getter,
			AppSettingKey fallback) {
		return find(guildId)
			.map(getter)
			.filter(value -> value != null && !value.isBlank())
			.orElseGet(() -> appSettings.get(fallback));
	}

	private BigDecimal decimalOverride(long guildId, java.util.function.Function<GuildConfig, BigDecimal> getter,
			AppSettingKey fallback) {
		return find(guildId)
			.map(getter)
			.orElseGet(() -> appSettings.getDecimal(fallback));
	}

	private int intOverride(long guildId, java.util.function.Function<GuildConfig, Integer> getter,
			AppSettingKey fallback) {
		return find(guildId)
			.map(getter)
			.orElseGet(() -> appSettings.getInt(fallback));
	}
}
