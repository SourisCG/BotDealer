/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One member's balance in one guild.
 *
 * <p>Wallets are per guild on purpose: two servers running BotDealer never share an
 * economy. The {@code @Version} column makes concurrent bets on the same wallet safe;
 * conflicting transactions are retried by {@code Retry.onConflict}.</p>
 */
@Entity
@Table(name = "wallets",
	uniqueConstraints = @UniqueConstraint(name = "uk_wallet_guild_user", columnNames = {"guild_id", "user_id"}),
	indexes = @Index(name = "ix_wallet_guild", columnList = "guild_id"))
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Wallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "guild_id", nullable = false)
	private long guildId;

	@Column(name = "user_id", nullable = false)
	private long userId;

	/** Last known display name, so the UI can show names without calling Discord. */
	@Column(name = "display_name", length = 100)
	private String displayName;

	@Column(name = "balance", nullable = false, precision = 19, scale = 2)
	private BigDecimal balance = BigDecimal.ZERO;

	@Column(name = "last_daily_at")
	private Instant lastDailyAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;
}
