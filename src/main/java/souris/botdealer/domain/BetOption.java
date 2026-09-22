/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One selectable outcome of an event.
 *
 * <p>{@code fixedOdds} is only meaningful for {@link PayoutMode#FIXED_ODDS} events and is
 * null for parimutuel ones. The column is named {@code option_order} because
 * {@code position} and {@code order} are reserved words in several databases.</p>
 */
@Entity
@Table(name = "bet_options",
	uniqueConstraints = @UniqueConstraint(name = "uk_option_event_order",
		columnNames = {"event_id", "option_order"}))
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class BetOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	@ToString.Exclude
	private BetEvent event;

	@Column(name = "label", nullable = false, length = 120)
	private String label;

	/** Multiplier applied to the stake, e.g. {@code 2.50}. Null for parimutuel. */
	@Column(name = "fixed_odds", precision = 10, scale = 2)
	private BigDecimal fixedOdds;

	@Column(name = "option_order", nullable = false)
	private int optionOrder;
}
