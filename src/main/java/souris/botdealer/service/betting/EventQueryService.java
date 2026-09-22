/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.betting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import souris.botdealer.domain.BetStatus;
import souris.botdealer.domain.BetEvent;
import souris.botdealer.domain.BetOption;
import souris.botdealer.domain.EventStatus;
import souris.botdealer.domain.PayoutMode;
import souris.botdealer.repository.BetEventRepository;
import souris.botdealer.repository.BetRepository;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.util.Money;

/**
 * Read model for events: everything the UI and the embeds need to show a live event.
 */
@Service
public class EventQueryService {

	private final BetEventRepository events;
	private final BetRepository bets;
	private final GuildConfigService guildConfig;

	public EventQueryService(BetEventRepository events, BetRepository bets, GuildConfigService guildConfig) {
		this.events = events;
		this.bets = bets;
		this.guildConfig = guildConfig;
	}

	/** A live view of one option: pool, bet count and the odds a bettor would get. */
	public record OptionView(long optionId, String label, BigDecimal fixedOdds, BigDecimal pool, long betCount,
			BigDecimal impliedOdds) {
	}

	public record EventView(long eventId, long guildId, String title, String description, PayoutMode payoutMode,
			EventStatus status, Instant closesAt, BigDecimal rakePercent, BigDecimal totalPool, long betCount,
			List<OptionView> options, Long winningOptionId) {
	}

	@Transactional(readOnly = true)
	public Optional<BetEvent> find(long eventId) {
		return events.findById(eventId);
	}

	@Transactional(readOnly = true)
	public List<BetEvent> listByGuild(long guildId) {
		return events.findByGuildIdOrderByCreatedAtDesc(guildId);
	}

	@Transactional(readOnly = true)
	public List<BetEvent> listAll() {
		return events.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	public List<BetEvent> openEvents(long guildId) {
		return events.findByGuildIdOrderByCreatedAtDesc(guildId).stream()
			.filter(event -> event.getStatus() == EventStatus.OPEN)
			.toList();
	}

	@Transactional(readOnly = true)
	public long openEventCount(long guildId) {
		return events.countByGuildIdAndStatus(guildId, EventStatus.OPEN);
	}

	/** Builds the full view of an event, including pools and implied odds. */
	@Transactional(readOnly = true)
	public Optional<EventView> view(long eventId) {
		return events.findById(eventId).map(this::toView);
	}

	@Transactional(readOnly = true)
	public EventView toView(BetEvent event) {
		List<BettingMath.Stake> stakes = stakesOf(event.getId());
		BigDecimal total = BettingMath.totalPool(stakes);

		List<OptionView> optionViews = event.getOptions().stream()
			.map(option -> {
				BigDecimal pool = BettingMath.poolForOption(stakes, option.getId());
				long count = stakes.stream().filter(stake -> stake.optionId() == option.getId()).count();
				BigDecimal implied = event.getPayoutMode() == PayoutMode.PARIMUTUEL
					? BettingMath.impliedOdds(pool, total, event.getRakePercent())
					: option.getFixedOdds();
				return new OptionView(option.getId(), option.getLabel(), option.getFixedOdds(), pool, count, implied);
			})
			.toList();

		return new EventView(event.getId(), event.getGuildId(), event.getTitle(), event.getDescription(),
			event.getPayoutMode(), event.getStatus(), event.getClosesAt(), event.getRakePercent(), total,
			stakes.size(), optionViews, event.getWinningOptionId());
	}

	/** Liability the creator would face for each option of a fixed-odds event. */
	@Transactional(readOnly = true)
	public Map<Long, BigDecimal> fixedOddsLiabilities(BetEvent event) {
		List<BettingMath.Stake> stakes = stakesOf(event.getId());
		Map<Long, BigDecimal> oddsByBet = oddsByBetId(event.getId());
		Map<Long, BigDecimal> liabilities = new java.util.LinkedHashMap<>();
		for (BetOption option : event.getOptions()) {
			liabilities.put(option.getId(),
				BettingMath.fixedOddsLiability(stakes, option.getId(), oddsByBet));
		}
		return liabilities;
	}

	/** Bets of an event as plain stakes, excluding refunded ones. */
	@Transactional(readOnly = true)
	public List<BettingMath.Stake> stakesOf(long eventId) {
		return bets.findByEventIdOrderByPlacedAtAsc(eventId).stream()
			.filter(bet -> bet.getStatus() != BetStatus.REFUNDED)
			.map(bet -> new BettingMath.Stake(bet.getId(), bet.getOptionId(), bet.getAmount()))
			.toList();
	}

	@Transactional(readOnly = true)
	public Map<Long, BigDecimal> oddsByBetId(long eventId) {
		Map<Long, BigDecimal> odds = new java.util.HashMap<>();
		bets.findByEventIdOrderByPlacedAtAsc(eventId)
			.forEach(bet -> odds.put(bet.getId(), bet.getOddsAtPlacement()));
		return odds;
	}

	@Transactional(readOnly = true)
	public BigDecimal minBet(long guildId) {
		return Money.normalize(guildConfig.minBet(guildId));
	}

	@Transactional(readOnly = true)
	public BigDecimal maxBet(long guildId) {
		return Money.normalize(guildConfig.maxBet(guildId));
	}
}
