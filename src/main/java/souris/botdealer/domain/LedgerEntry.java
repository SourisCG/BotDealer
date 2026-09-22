/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Append-only audit trail. Rows are never updated or deleted (except by a full app
 * reset), so a balance can always be explained by replaying its entries.
 */
@Entity
@Table(name = "ledger_entries",
	indexes = {
		@Index(name = "ix_ledger_guild_user", columnList = "guild_id,user_id"),
		@Index(name = "ix_ledger_created", columnList = "created_at")
	})
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "guild_id", nullable = false)
	private long guildId;

	@Column(name = "user_id", nullable = false)
	private long userId;

	/** Signed change applied to the balance. */
	@Column(name = "amount_delta", nullable = false, precision = 19, scale = 2)
	private BigDecimal delta;

	/** Balance after applying the change, so the trail is self-contained. */
	@Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
	private BigDecimal balanceAfter;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 32)
	private LedgerReason reason;

	/** Optional link to the event/bet that caused the movement. */
	@Column(name = "reference_id")
	private Long referenceId;

	/** Who caused it, when it was not the wallet owner (admin actions). */
	@Column(name = "actor_id")
	private Long actorId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
