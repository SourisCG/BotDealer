/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A betting event: "who wins X", with one or more options.
 *
 * <p>The payout mode and the rake are captured when the event is created, so changing a
 * setting later never rewrites the rules of an event that people already bet on.</p>
 */
@Entity
@Table(name = "bet_events",
	indexes = {
		@Index(name = "ix_event_guild_status", columnList = "guild_id,status"),
		@Index(name = "ix_event_closes", columnList = "closes_at")
	})
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class BetEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "guild_id", nullable = false)
	private long guildId;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", length = 1000)
	private String description;

	/** Discord user id of whoever created the event. */
	@Column(name = "created_by", nullable = false)
	private long createdBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "payout_mode", nullable = false, length = 24)
	private PayoutMode payoutMode = PayoutMode.PARIMUTUEL;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private EventStatus status = EventStatus.DRAFT;

	/** Bets stop being accepted after this instant; null means manual closing only. */
	@Column(name = "closes_at")
	private Instant closesAt;

	/** House edge in percent, captured at creation. 0 means the whole pot is paid out. */
	@Column(name = "rake_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal rakePercent = BigDecimal.ZERO;

	/** Channel where the event was announced, and the live embed message. */
	@Column(name = "channel_id")
	private Long channelId;

	@Column(name = "message_id")
	private Long messageId;

	@Column(name = "winning_option_id")
	private Long winningOptionId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "settled_at")
	private Instant settledAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@OrderBy("optionOrder ASC")
	@ToString.Exclude
	private List<BetOption> options = new ArrayList<>();

	/** Adds an option and keeps both sides of the association in sync. */
	public void addOption(BetOption option) {
		option.setEvent(this);
		option.setOptionOrder(options.size());
		options.add(option);
	}
}
