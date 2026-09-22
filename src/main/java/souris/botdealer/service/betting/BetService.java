/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.betting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import souris.botdealer.repository.BetOptionRepository;
import souris.botdealer.repository.BetRepository;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.service.economy.WalletService;
import souris.botdealer.util.Money;
import souris.botdealer.util.Retry;

/**
 * Places bets.
 *
 * <p>Validation, the wallet debit and the bet row all happen in one transaction, so a
 * rejected bet never moves money and a failed write never leaves a paid-for bet behind.
 * The whole transaction is retried on an optimistic-lock conflict.</p>
 */
@Service
public class BetService {

	private static final Logger log = LoggerFactory.getLogger(BetService.class);

	private final BetRepository bets;
	private final BetEventRepository events;
	private final BetOptionRepository options;
	private final WalletService wallets;
	private final GuildConfigService guildConfig;
	private final TransactionTemplate transactions;

	public BetService(BetRepository bets, BetEventRepository events, BetOptionRepository options,
			WalletService wallets, GuildConfigService guildConfig, TransactionTemplate transactions) {
		this.bets = bets;
		this.events = events;
		this.options = options;
		this.wallets = wallets;
		this.guildConfig = guildConfig;
		this.transactions = transactions;
	}

	/** Confirmation returned to the caller so it can render a receipt. */
	public record PlacedBet(long betId, long eventId, long optionId, String optionLabel, BigDecimal amount,
			BigDecimal oddsAtPlacement, BigDecimal balanceAfter) {
	}

	public PlacedBet place(long guildId, long userId, String displayName, long eventId, long optionId,
			BigDecimal amount) {
		return Retry.onConflict(() -> transactions.execute(status -> {
			BetEvent event = events.findById(eventId)
				.orElseThrow(() -> new BetValidationException("bet.error.eventNotFound"));

			if (event.getGuildId() != guildId) {
				throw new BetValidationException("bet.error.wrongGuild");
			}
			if (!event.getStatus().acceptsBets()) {
				throw new BetValidationException("bet.error.notOpen");
			}
			if (event.getClosesAt() != null && event.getClosesAt().isBefore(Instant.now())) {
				throw new BetValidationException("bet.error.closed");
			}

			BetOption option = options.findById(optionId)
				.filter(candidate -> candidate.getEvent().getId().equals(eventId))
				.orElseThrow(() -> new BetValidationException("bet.error.optionNotFound"));

			BigDecimal stake = Money.normalize(amount);
			if (!Money.isPositive(stake)) {
				throw new BetValidationException("bet.error.amountPositive");
			}
			BigDecimal minimum = Money.normalize(guildConfig.minBet(guildId));
			if (minimum.signum() > 0 && stake.compareTo(minimum) < 0) {
				throw new BetValidationException("bet.error.belowMinimum", minimum.toPlainString());
			}
			BigDecimal maximum = Money.normalize(guildConfig.maxBet(guildId));
			if (maximum.signum() > 0 && stake.compareTo(maximum) > 0) {
				throw new BetValidationException("bet.error.aboveMaximum", maximum.toPlainString());
			}

			var wallet = wallets.debitInTransaction(guildId, userId, displayName, stake,
				LedgerReason.BET_PLACED, eventId, null);

			Bet bet = new Bet(null, eventId, optionId, guildId, userId, stake,
				event.getPayoutMode() == PayoutMode.FIXED_ODDS ? option.getFixedOdds() : null,
				BetStatus.PLACED, null, Instant.now(), null);
			bets.save(bet);

			log.debug("User {} staked {} on option {} of event {}", userId, stake, optionId, eventId);
			return new PlacedBet(bet.getId(), eventId, optionId, option.getLabel(), stake,
				bet.getOddsAtPlacement(), wallet.getBalance());
		}));
	}

	@Transactional(readOnly = true)
	public List<Bet> betsOfEvent(long eventId) {
		return bets.findByEventIdOrderByPlacedAtAsc(eventId);
	}

	@Transactional(readOnly = true)
	public List<Bet> placedBetsOfEvent(long eventId) {
		return bets.findByEventIdAndStatus(eventId, BetStatus.PLACED);
	}

	@Transactional(readOnly = true)
	public List<Bet> betsOf(long guildId, long userId) {
		return bets.findByGuildIdAndUserIdOrderByPlacedAtDesc(guildId, userId);
	}

	@Transactional(readOnly = true)
	public List<Bet> betsOf(long guildId, long userId, long eventId) {
		return bets.findByGuildIdAndUserIdOrderByPlacedAtDesc(guildId, userId).stream()
			.filter(bet -> bet.getEventId() == eventId)
			.toList();
	}

	@Transactional(readOnly = true)
	public long betCount(long eventId) {
		return bets.countByEventId(eventId);
	}

	@Transactional(readOnly = true)
	public Optional<Bet> find(long betId) {
		return bets.findById(betId);
	}
}
