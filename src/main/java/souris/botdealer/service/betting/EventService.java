/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.betting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import souris.botdealer.domain.Bet;
import souris.botdealer.domain.BetEvent;
import souris.botdealer.domain.BetOption;
import souris.botdealer.domain.BetStatus;
import souris.botdealer.domain.EventStatus;
import souris.botdealer.domain.LedgerReason;
import souris.botdealer.domain.PayoutMode;
import souris.botdealer.repository.BetEventRepository;
import souris.botdealer.repository.BetRepository;
import souris.botdealer.service.economy.WalletService;
import souris.botdealer.util.Money;
import souris.botdealer.util.Retry;

/**
 * Creates and resolves events.
 *
 * <p>Settlement is one transaction: every winner is credited, every loser is marked and
 * the event flips to SETTLED together. If anything fails, the event stays open and no
 * money moved.</p>
 */
@Service
public class EventService {

	private static final Logger log = LoggerFactory.getLogger(EventService.class);

	private static final BigDecimal MAX_RAKE = new BigDecimal("50");
	private static final int MIN_OPTIONS = 2;

	/** Draft option supplied by the UI or a command. */
	public record OptionDraft(String label, BigDecimal fixedOdds) {
	}

	/** Everything needed to create an event. */
	public record CreateRequest(long guildId, long createdBy, String title, String description,
			PayoutMode payoutMode, Instant closesAt, BigDecimal rakePercent, Long channelId,
			List<OptionDraft> options, boolean openImmediately) {
	}

	/** What happened when an event was resolved, ready to be announced. */
	public record SettlementReport(long eventId, long winningOptionId, String winningOptionLabel, int winnerCount,
			int loserCount, BigDecimal totalPool, BigDecimal totalPaid, BigDecimal rakeCollected,
			boolean refundedInstead, String outcomeKey) {
	}

	private final BetEventRepository events;
	private final BetRepository bets;
	private final WalletService wallets;
	private final TransactionTemplate transactions;

	public EventService(BetEventRepository events, BetRepository bets, WalletService wallets,
			TransactionTemplate transactions) {
		this.events = events;
		this.bets = bets;
		this.wallets = wallets;
		this.transactions = transactions;
	}

	// ------------------------------------------------------------------ lifecycle

	public BetEvent create(CreateRequest request) {
		String title = requireText(request.title(), "event.error.titleRequired", 200);
		String description = request.description() == null ? "" : request.description().strip();
		if (description.length() > 1000) {
			throw new BetValidationException("event.error.descriptionTooLong");
		}
		if (request.options() == null || request.options().size() < MIN_OPTIONS) {
			throw new BetValidationException("event.error.needsOptions", MIN_OPTIONS);
		}
		BigDecimal rake = request.rakePercent() == null ? BigDecimal.ZERO : request.rakePercent();
		if (rake.signum() < 0 || rake.compareTo(MAX_RAKE) > 0) {
			throw new BetValidationException("event.error.rakeRange", MAX_RAKE.toPlainString());
		}
		if (request.closesAt() != null && request.closesAt().isBefore(Instant.now())) {
			throw new BetValidationException("event.error.closeInPast");
		}

		return Retry.onConflict(() -> transactions.execute(status -> {
			BetEvent event = new BetEvent();
			event.setGuildId(request.guildId());
			event.setCreatedBy(request.createdBy());
			event.setTitle(title);
			event.setDescription(description);
			event.setPayoutMode(request.payoutMode() == null ? PayoutMode.PARIMUTUEL : request.payoutMode());
			event.setStatus(request.openImmediately() ? EventStatus.OPEN : EventStatus.DRAFT);
			event.setClosesAt(request.closesAt());
			event.setRakePercent(rake.setScale(2, java.math.RoundingMode.HALF_UP));
			event.setChannelId(request.channelId());
			event.setCreatedAt(Instant.now());

			for (OptionDraft draft : request.options()) {
				String label = requireText(draft.label(), "event.error.optionLabelRequired", 120);
				BigDecimal odds = null;
				if (event.getPayoutMode() == PayoutMode.FIXED_ODDS) {
					odds = draft.fixedOdds();
					if (odds == null || odds.compareTo(BigDecimal.ONE) <= 0) {
						throw new BetValidationException("event.error.oddsTooLow", label);
					}
					odds = odds.setScale(2, java.math.RoundingMode.HALF_UP);
				}
				BetOption option = new BetOption();
				option.setLabel(label);
				option.setFixedOdds(odds);
				event.addOption(option);
			}

			BetEvent saved = events.save(event);
			log.info("Event {} created in guild {} ({} options, {})", saved.getId(), request.guildId(),
				saved.getOptions().size(), saved.getPayoutMode());
			return saved;
		}));
	}

	public BetEvent open(long eventId) {
		return transition(eventId, EventStatus.OPEN, event -> {
			if (event.getStatus() != EventStatus.DRAFT) {
				throw new BetValidationException("event.error.onlyDraftCanOpen");
			}
		});
	}

	public BetEvent close(long eventId) {
		return transition(eventId, EventStatus.CLOSED, event -> {
			if (event.getStatus() != EventStatus.OPEN) {
				throw new BetValidationException("event.error.onlyOpenCanClose");
			}
			event.setClosedAt(Instant.now());
		});
	}

	/**
	 * Closes every open event whose betting window elapsed.
	 *
	 * @return the ids that were closed, so callers can announce them
	 */
	public List<Long> closeExpired() {
		List<BetEvent> expired = events.findByStatusAndClosesAtBefore(EventStatus.OPEN, Instant.now());
		List<Long> closed = new ArrayList<>();
		for (BetEvent event : expired) {
			try {
				close(event.getId());
				closed.add(event.getId());
			} catch (RuntimeException e) {
				log.warn("Could not auto-close event {}: {}", event.getId(), e.toString());
			}
		}
		return closed;
	}

	// ------------------------------------------------------------------ settlement

	/** Resolves an event, paying the winners of {@code winningOptionId}. */
	public SettlementReport settle(long eventId, long winningOptionId) {
		return Retry.onConflict(() -> transactions.execute(status -> {
			BetEvent event = events.findById(eventId)
				.orElseThrow(() -> new BetValidationException("event.error.notFound"));
			if (event.getStatus().isFinal()) {
				throw new BetValidationException("event.error.alreadyFinal");
			}
			BetOption winner = event.getOptions().stream()
				.filter(option -> option.getId() != null && option.getId() == winningOptionId)
				.findFirst()
				.orElseThrow(() -> new BetValidationException("event.error.optionNotFound"));

			List<Bet> placed = bets.findByEventIdAndStatus(eventId, BetStatus.PLACED);
			Instant now = Instant.now();

			if (placed.isEmpty()) {
				return finish(event, winner, now, new SettlementReport(eventId, winningOptionId,
					winner.getLabel(), 0, 0, Money.ZERO, Money.ZERO, Money.ZERO, false, "settle.result.noBets"));
			}

			List<BettingMath.Stake> stakes = placed.stream()
				.map(bet -> new BettingMath.Stake(bet.getId(), bet.getOptionId(), bet.getAmount()))
				.toList();
			BigDecimal totalPool = BettingMath.totalPool(stakes);

			List<BettingMath.Payout> payouts = event.getPayoutMode() == PayoutMode.PARIMUTUEL
				? BettingMath.parimutuelPayouts(stakes, winningOptionId, event.getRakePercent())
				: BettingMath.fixedOddsPayouts(stakes, winningOptionId, oddsByBetId(placed));

			// Nobody backed the winning option: refund everyone rather than let the pot vanish.
			if (payouts.isEmpty()) {
				refundAll(placed, eventId, now);
				return finish(event, winner, now, new SettlementReport(eventId, winningOptionId,
					winner.getLabel(), 0, placed.size(), totalPool, totalPool, Money.ZERO, true,
					"settle.result.refundedNoWinners"));
			}

			Map<Long, Bet> byId = new HashMap<>();
			placed.forEach(bet -> byId.put(bet.getId(), bet));

			BigDecimal totalPaid = Money.ZERO;
			int winnerCount = 0;
			int loserCount = 0;
			for (BettingMath.Payout payout : payouts) {
				Bet bet = byId.get(payout.betId());
				bet.setStatus(BetStatus.WON);
				bet.setPayout(payout.amount());
				bet.setSettledAt(now);
				bets.save(bet);
				if (Money.isPositive(payout.amount())) {
					wallets.creditInTransaction(event.getGuildId(), bet.getUserId(), null, payout.amount(),
						LedgerReason.BET_PAYOUT, eventId, null);
				}
				totalPaid = totalPaid.add(payout.amount());
				winnerCount++;
			}
			for (Bet bet : placed) {
				if (bet.getStatus() == BetStatus.PLACED) {
					bet.setStatus(BetStatus.LOST);
					bet.setPayout(Money.ZERO);
					bet.setSettledAt(now);
					bets.save(bet);
					loserCount++;
				}
			}

			BigDecimal rake = event.getPayoutMode() == PayoutMode.PARIMUTUEL
				? BettingMath.rakeAmount(totalPool, event.getRakePercent())
				: Money.ZERO;
			if (Money.isPositive(rake)) {
				wallets.creditHouseInTransaction(event.getGuildId(), rake, LedgerReason.RAKE, eventId);
			}

			log.info("Event {} settled: {} winners, {} losers, paid {}, rake {}", eventId, winnerCount,
				loserCount, totalPaid, rake);
			return finish(event, winner, now, new SettlementReport(eventId, winningOptionId, winner.getLabel(),
				winnerCount, loserCount, totalPool, totalPaid, rake, false, "settle.result.paid"));
		}));
	}

	/** Cancels an event and returns every stake. */
	public SettlementReport cancel(long eventId) {
		return Retry.onConflict(() -> transactions.execute(status -> {
			BetEvent event = events.findById(eventId)
				.orElseThrow(() -> new BetValidationException("event.error.notFound"));
			if (event.getStatus().isFinal()) {
				throw new BetValidationException("event.error.alreadyFinal");
			}
			List<Bet> placed = bets.findByEventIdAndStatus(eventId, BetStatus.PLACED);
			Instant now = Instant.now();
			refundAll(placed, eventId, now);

			BigDecimal totalPool = BettingMath.totalPool(placed.stream()
				.map(bet -> new BettingMath.Stake(bet.getId(), bet.getOptionId(), bet.getAmount()))
				.toList());

			event.setStatus(EventStatus.CANCELLED);
			event.setSettledAt(now);
			event.setUpdatedAt(now);
			events.save(event);
			log.info("Event {} cancelled, {} bets refunded", eventId, placed.size());
			return new SettlementReport(eventId, 0L, "", 0, placed.size(), totalPool, totalPool,
				Money.ZERO, true, "settle.result.cancelled");
		}));
	}

	// ------------------------------------------------------------------ helpers

	private SettlementReport finish(BetEvent event, BetOption winner, Instant now, SettlementReport report) {
		event.setWinningOptionId(winner.getId());
		event.setStatus(EventStatus.SETTLED);
		event.setSettledAt(now);
		event.setUpdatedAt(now);
		events.save(event);
		return report;
	}

	private void refundAll(List<Bet> placed, long eventId, Instant now) {
		for (Bet bet : placed) {
			bet.setStatus(BetStatus.REFUNDED);
			bet.setPayout(bet.getAmount());
			bet.setSettledAt(now);
			bets.save(bet);
			wallets.creditInTransaction(bet.getGuildId(), bet.getUserId(), null, bet.getAmount(),
				LedgerReason.BET_REFUND, eventId, null);
		}
	}

	private Map<Long, BigDecimal> oddsByBetId(List<Bet> placed) {
		Map<Long, BigDecimal> odds = new HashMap<>();
		placed.forEach(bet -> odds.put(bet.getId(), bet.getOddsAtPlacement()));
		return odds;
	}

	private BetEvent transition(long eventId, EventStatus target,
			java.util.function.Consumer<BetEvent> guard) {
		return Retry.onConflict(() -> transactions.execute(status -> {
			BetEvent event = events.findById(eventId)
				.orElseThrow(() -> new BetValidationException("event.error.notFound"));
			guard.accept(event);
			event.setStatus(target);
			event.setUpdatedAt(Instant.now());
			return events.save(event);
		}));
	}

	private static String requireText(String value, String messageKey, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new BetValidationException(messageKey);
		}
		String trimmed = value.strip();
		if (trimmed.length() > maxLength) {
			throw new BetValidationException("event.error.textTooLong", maxLength);
		}
		return trimmed;
	}

	@Transactional(readOnly = true)
	public Optional<BetEvent> find(long eventId) {
		return events.findById(eventId);
	}

	/** Used by tests and the UI to build a list of drafts quickly. */
	public static List<OptionDraft> drafts(String... labels) {
		List<OptionDraft> drafts = new ArrayList<>();
		for (String label : labels) {
			drafts.add(new OptionDraft(label, null));
		}
		return drafts;
	}
}
