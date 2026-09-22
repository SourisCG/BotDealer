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
 * A stake placed by one member on one option.
 *
 * <p>{@code oddsAtPlacement} freezes fixed odds at bet time: later edits to the option
 * cannot change what an existing bet is worth. It stays null for parimutuel bets, whose
 * payout depends on the final pool.</p>
 */
@Entity
@Table(name = "bets",
	indexes = {
		@Index(name = "ix_bet_event", columnList = "event_id"),
		@Index(name = "ix_bet_event_option", columnList = "event_id,option_id"),
		@Index(name = "ix_bet_guild_user", columnList = "guild_id,user_id")
	})
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Bet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false)
	private long eventId;

	@Column(name = "option_id", nullable = false)
	private long optionId;

	@Column(name = "guild_id", nullable = false)
	private long guildId;

	@Column(name = "user_id", nullable = false)
	private long userId;

	@Column(name = "amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "odds_at_placement", precision = 10, scale = 2)
	private BigDecimal oddsAtPlacement;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private BetStatus status = BetStatus.PLACED;

	/** Amount credited when the bet won or was refunded. */
	@Column(name = "payout", precision = 19, scale = 2)
	private BigDecimal payout;

	@Column(name = "placed_at", nullable = false)
	private Instant placedAt = Instant.now();

	@Column(name = "settled_at")
	private Instant settledAt;
}
