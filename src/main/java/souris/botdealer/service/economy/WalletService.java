/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.economy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import souris.botdealer.domain.LedgerReason;
import souris.botdealer.domain.Wallet;
import souris.botdealer.repository.WalletRepository;
import souris.botdealer.util.Money;
import souris.botdealer.util.Retry;

/**
 * The only way money moves.
 *
 * <p>Every mutation runs inside a {@link TransactionTemplate} so it can be retried as a
 * whole when two operations race on the same wallet ({@code @Version} conflict). A plain
 * {@code @Transactional} method could not be retried safely: the failed transaction would
 * already be marked rollback-only.</p>
 *
 * <p>Balance changes always write a ledger entry in the same transaction.</p>
 */
@Service
public class WalletService {

	private static final Logger log = LoggerFactory.getLogger(WalletService.class);

	/** Sentinel user id for the per-guild house wallet that collects the rake. */
	public static final long HOUSE_USER_ID = 0L;

	private final WalletRepository wallets;
	private final GuildConfigService guildConfig;
	private final LedgerService ledger;
	private final TransactionTemplate transactions;

	public WalletService(WalletRepository wallets, GuildConfigService guildConfig, LedgerService ledger,
			TransactionTemplate transactions) {
		this.wallets = wallets;
		this.guildConfig = guildConfig;
		this.ledger = ledger;
		this.transactions = transactions;
	}

	// -------------------------------------------------------------------- reads

	@Transactional(readOnly = true)
	public Optional<Wallet> find(long guildId, long userId) {
		return wallets.findByGuildIdAndUserId(guildId, userId);
	}

	@Transactional(readOnly = true)
	public List<Wallet> listByGuild(long guildId) {
		return wallets.findByGuildIdOrderByBalanceDesc(guildId);
	}

	@Transactional(readOnly = true)
	public List<Wallet> search(long guildId, String name) {
		if (name == null || name.isBlank()) {
			return listByGuild(guildId);
		}
		return wallets.findByGuildIdAndDisplayNameContainingIgnoreCaseOrderByBalanceDesc(guildId, name.strip());
	}

	@Transactional(readOnly = true)
	public long count(long guildId) {
		return wallets.countByGuildId(guildId);
	}

	@Transactional(readOnly = true)
	public BigDecimal totalInCirculation(long guildId) {
		BigDecimal total = wallets.totalBalance(guildId);
		return total == null ? Money.ZERO : total;
	}

	/** @return the wallet, creating it with the guild's starting balance when missing */
	public Wallet getOrCreate(long guildId, long userId, String displayName) {
		return Retry.onConflict(() -> transactions.execute(status -> {
			Optional<Wallet> existing = wallets.findByGuildIdAndUserId(guildId, userId);
			if (existing.isPresent()) {
				Wallet wallet = existing.get();
				if (displayName != null && !displayName.isBlank() && !displayName.equals(wallet.getDisplayName())) {
					wallet.setDisplayName(displayName);
					wallet.setUpdatedAt(Instant.now());
					wallets.save(wallet);
				}
				return wallet;
			}
			BigDecimal starting = Money.normalize(guildConfig.startingBalance(guildId));
			Wallet wallet = new Wallet(null, guildId, userId, displayName, starting, null,
				Instant.now(), Instant.now(), 0L);
			wallets.save(wallet);
			if (Money.isPositive(starting)) {
				ledger.record(guildId, userId, starting, starting, LedgerReason.STARTING_BALANCE, null, null);
			}
			log.info("Created wallet for user {} in guild {} with starting balance {}", userId, guildId, starting);
			return wallet;
		}));
	}

	// ---------------------------------------------------------------- mutations

	/**
	 * Credits an amount in the caller's transaction, without opening one or retrying.
	 *
	 * <p>This is the building block for operations that must move money and do something
	 * else atomically (placing a bet, settling an event). The caller owns the transaction
	 * and the retry; see {@link #deposit} for the self-contained version.</p>
	 */
	public Wallet creditInTransaction(long guildId, long userId, String displayName, BigDecimal amount,
			LedgerReason reason, Long referenceId, Long actorId) {
		BigDecimal credit = Money.normalize(amount);
		if (!Money.isPositive(credit)) {
			throw new IllegalArgumentException("Credit amount must be positive");
		}
		Wallet wallet = loadOrCreate(guildId, userId, displayName);
		BigDecimal after = Money.normalize(wallet.getBalance().add(credit));
		wallet.setBalance(after);
		wallet.setUpdatedAt(Instant.now());
		wallets.save(wallet);
		ledger.record(guildId, userId, credit, after, reason, referenceId, actorId);
		return wallet;
	}

	/**
	 * Debits an amount in the caller's transaction, without opening one or retrying.
	 *
	 * @throws InsufficientFundsException when the balance would go below zero
	 */
	public Wallet debitInTransaction(long guildId, long userId, String displayName, BigDecimal amount,
			LedgerReason reason, Long referenceId, Long actorId) {
		BigDecimal debit = Money.normalize(amount);
		if (!Money.isPositive(debit)) {
			throw new IllegalArgumentException("Debit amount must be positive");
		}
		Wallet wallet = loadOrCreate(guildId, userId, displayName);
		BigDecimal balance = Money.normalize(wallet.getBalance());
		if (balance.compareTo(debit) < 0) {
			throw new InsufficientFundsException(balance.toPlainString(), debit.toPlainString());
		}
		BigDecimal after = Money.normalize(balance.subtract(debit));
		wallet.setBalance(after);
		wallet.setUpdatedAt(Instant.now());
		wallets.save(wallet);
		ledger.record(guildId, userId, debit.negate(), after, reason, referenceId, actorId);
		return wallet;
	}

	/**
	 * Credits the per-guild house wallet (rake) in the caller's transaction.
	 *
	 * <p>The house wallet is created with a <b>zero</b> balance: it must never receive the
	 * member starting balance, otherwise collecting the rake would mint money out of
	 * nowhere and inflate the economy.</p>
	 */
	public Wallet creditHouseInTransaction(long guildId, BigDecimal amount, LedgerReason reason,
			Long referenceId) {
		BigDecimal credit = Money.normalize(amount);
		if (!Money.isPositive(credit)) {
			throw new IllegalArgumentException("House credit must be positive");
		}
		Wallet wallet = loadOrCreate(guildId, HOUSE_USER_ID, "House", false);
		BigDecimal after = Money.normalize(wallet.getBalance().add(credit));
		wallet.setBalance(after);
		wallet.setUpdatedAt(Instant.now());
		wallets.save(wallet);
		ledger.record(guildId, HOUSE_USER_ID, credit, after, reason, referenceId, null);
		return wallet;
	}

	/** Credits an amount. Creates the wallet first when needed. */
	public Wallet deposit(long guildId, long userId, String displayName, BigDecimal amount,
			LedgerReason reason, Long referenceId, Long actorId) {
		return Retry.onConflict(() -> transactions.execute(status ->
			creditInTransaction(guildId, userId, displayName, amount, reason, referenceId, actorId)));
	}

	/** Debits an amount, failing when the balance is not enough. */
	public Wallet withdraw(long guildId, long userId, String displayName, BigDecimal amount,
			LedgerReason reason, Long referenceId, Long actorId) {
		return Retry.onConflict(() -> transactions.execute(status ->
			debitInTransaction(guildId, userId, displayName, amount, reason, referenceId, actorId)));
	}

	/**
	 * Applies an admin adjustment in either direction. A negative delta that would push
	 * the balance below zero is rejected like any other withdrawal.
	 */
	public Wallet adjust(long guildId, long userId, String displayName, BigDecimal delta, long actorId) {
		BigDecimal change = Money.normalize(delta);
		if (change.signum() == 0) {
			throw new IllegalArgumentException("Adjustment must not be zero");
		}
		LedgerReason reason = change.signum() > 0 ? LedgerReason.ADMIN_GRANT : LedgerReason.ADMIN_REMOVE;
		if (change.signum() > 0) {
			return deposit(guildId, userId, displayName, change, reason, null, actorId);
		}
		return withdraw(guildId, userId, displayName, change.negate(), reason, null, actorId);
	}

	/**
	 * Credits the periodic reward and stamps the cooldown in a single transaction.
	 *
	 * <p>The cooldown is re-checked *inside* the transaction on purpose: checking it
	 * outside would let two simultaneous claims both pass the check and get paid twice.
	 * The {@code @Version} retry then makes concurrent claims resolve cleanly.</p>
	 */
	public DailyClaim claimDaily(long guildId, long userId, String displayName, BigDecimal amount,
			java.time.Duration cooldown) {
		BigDecimal reward = Money.normalize(amount);
		return Retry.onConflict(() -> transactions.execute(status -> {
			Wallet wallet = loadOrCreate(guildId, userId, displayName);
			Instant now = Instant.now();
			Instant nextEligible = wallet.getLastDailyAt() == null
				? now
				: wallet.getLastDailyAt().plus(cooldown);
			if (nextEligible.isAfter(now)) {
				return new DailyClaim(false, wallet.getBalance(), nextEligible);
			}
			BigDecimal after = Money.normalize(wallet.getBalance().add(reward));
			wallet.setBalance(after);
			wallet.setLastDailyAt(now);
			wallet.setUpdatedAt(now);
			wallets.save(wallet);
			ledger.record(guildId, userId, reward, after, LedgerReason.DAILY, null, null);
			return new DailyClaim(true, after, now.plus(cooldown));
		}));
	}

	/** Outcome of an atomic daily claim. */
	public record DailyClaim(boolean claimed, BigDecimal balance, Instant nextEligibleAt) {
	}

	/** Loads the wallet or creates it with the starting balance; must run in a transaction. */
	private Wallet loadOrCreate(long guildId, long userId, String displayName) {
		return loadOrCreate(guildId, userId, displayName, true);
	}

	/**
	 * Loads the wallet, creating it when missing.
	 *
	 * @param grantStartingBalance false for the house wallet, which starts at zero
	 */
	private Wallet loadOrCreate(long guildId, long userId, String displayName, boolean grantStartingBalance) {
		return wallets.findByGuildIdAndUserId(guildId, userId).orElseGet(() -> {
			BigDecimal starting = grantStartingBalance
				? Money.normalize(guildConfig.startingBalance(guildId))
				: Money.ZERO;
			Wallet wallet = new Wallet(null, guildId, userId, displayName, starting, null,
				Instant.now(), Instant.now(), 0L);
			wallets.save(wallet);
			if (Money.isPositive(starting)) {
				ledger.record(guildId, userId, starting, starting, LedgerReason.STARTING_BALANCE, null, null);
			}
			return wallet;
		});
	}
}
