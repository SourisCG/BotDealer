/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Per-guild overrides for the economy and moderation settings.
 *
 * <p>Every field except the id is nullable: {@code null} means "use the application
 * default" from {@code AppSettingKey}. {@code GuildConfigService} is the only place
 * that merges the two, so the fallback rules live in one spot.</p>
 */
@Entity
@Table(name = "guild_config")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class GuildConfig {

	/** Discord guild id (snowflake). Assigned by Discord, so no generation strategy. */
	@Id
	@Column(name = "guild_id", nullable = false)
	private Long guildId;

	/** Language override for this guild, e.g. {@code es} or {@code en}. */
	@Column(length = 8)
	private String locale;

	@Column(name = "currency_singular", length = 60)
	private String currencySingular;

	@Column(name = "currency_plural", length = 60)
	private String currencyPlural;

	@Column(name = "currency_symbol", length = 16)
	private String currencySymbol;

	@Column(name = "starting_balance", precision = 19, scale = 2)
	private BigDecimal startingBalance;

	@Column(name = "daily_amount")
	private Integer dailyAmount;

	@Column(name = "daily_cooldown_hours")
	private Integer dailyCooldownHours;

	/** Minimum stake; null means the application default. */
	@Column(name = "min_bet", precision = 19, scale = 2)
	private BigDecimal minBet;

	/** Maximum stake; {@code 0} means unlimited. */
	@Column(name = "max_bet", precision = 19, scale = 2)
	private BigDecimal maxBet;

	/** Role allowed to manage events and balances; null means Manage Server permission. */
	@Column(name = "admin_role_id")
	private Long adminRoleId;

	/** Channel where settled events are announced; null means the originating channel. */
	@Column(name = "announce_channel_id")
	private Long announceChannelId;

	/** Channel where bets are accepted; null means anywhere. */
	@Column(name = "betting_channel_id")
	private Long bettingChannelId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at")
	private Instant updatedAt;
}
