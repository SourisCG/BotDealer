/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.economy;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import souris.botdealer.domain.LedgerEntry;
import souris.botdealer.domain.LedgerReason;
import souris.botdealer.domain.Wallet;
import souris.botdealer.repository.AppSettingRepository;
import souris.botdealer.repository.BetRepository;
import souris.botdealer.repository.GuildConfigRepository;
import souris.botdealer.repository.LedgerEntryRepository;
import souris.botdealer.repository.WalletRepository;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WalletServiceTest {

	private static final long GUILD = 111_222_333L;
	private static final long USER = 444_555_666L;
	private static final long OTHER_USER = 777_888_999L;

	@Autowired
	private WalletService wallets;
	@Autowired
	private GuildConfigService guildConfig;
	@Autowired
	private DailyRewardService daily;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private LedgerEntryRepository ledgerRepository;
	@Autowired
	private GuildConfigRepository guildConfigRepository;
	@Autowired
	private AppSettingRepository appSettingRepository;
	@Autowired
	private AppSettingsService appSettings;
	@Autowired
	private BetRepository betRepository;

	@BeforeEach
	void cleanSlate() {
		ledgerRepository.deleteAllInBatch();
		betRepository.deleteAllInBatch();
		walletRepository.deleteAllInBatch();
		guildConfigRepository.deleteAllInBatch();
		appSettingRepository.deleteAllInBatch();
	}

	@Test
	void createsAWalletWithTheStartingBalanceAndOneLedgerEntry() {
		Wallet wallet = wallets.getOrCreate(GUILD, USER, "Ada");

		assertEquals(new BigDecimal("100.00"), wallet.getBalance());
		assertEquals(1, ledgerRepository.findByGuildIdAndUserIdOrderByCreatedAtDescIdDesc(GUILD, USER,
			org.springframework.data.domain.PageRequest.of(0, 10)).size());
	}

	@Test
	void startingBalanceIsGrantedOnlyOnce() {
		wallets.getOrCreate(GUILD, USER, "Ada");
		wallets.deposit(GUILD, USER, "Ada", new BigDecimal("50"), LedgerReason.ADMIN_GRANT, null, 1L);

		Wallet again = wallets.getOrCreate(GUILD, USER, "Ada");

		assertEquals(new BigDecimal("150.00"), again.getBalance());
	}

	@Test
	void guildOverridesTheStartingBalance() {
		var config = guildConfig.getOrCreate(GUILD);
		config.setStartingBalance(new BigDecimal("25.50"));
		guildConfig.save(config);

		Wallet wallet = wallets.getOrCreate(GUILD, USER, "Ada");

		assertEquals(new BigDecimal("25.50"), wallet.getBalance());
	}

	@Test
	void depositAndWithdrawKeepTheBalanceConsistent() {
		wallets.deposit(GUILD, USER, "Ada", new BigDecimal("40.25"), LedgerReason.ADMIN_GRANT, null, 1L);
		Wallet afterWithdraw = wallets.withdraw(GUILD, USER, "Ada", new BigDecimal("30.25"),
			LedgerReason.BET_PLACED, 9L, null);

		assertEquals(new BigDecimal("110.00"), afterWithdraw.getBalance());
		assertEquals(new BigDecimal("110.00"), wallets.find(GUILD, USER).orElseThrow().getBalance());
	}

	@Test
	void refusesToOverdraw() {
		Wallet created = wallets.getOrCreate(GUILD, USER, "Ada");

		InsufficientFundsException failure = assertThrows(InsufficientFundsException.class,
			() -> wallets.withdraw(GUILD, USER, "Ada", new BigDecimal("1000"), LedgerReason.BET_PLACED, null, null));

		assertEquals("100.00", failure.balance());
		assertEquals("1000.00", failure.requested());
		assertEquals(created.getBalance(), wallets.find(GUILD, USER).orElseThrow().getBalance());
	}

	@Test
	void failedFirstWithdrawalLeavesNoWalletBehind() {
		// The whole transaction rolls back, including the wallet that loadOrCreate had to
		// create, so a rejected bet cannot leave a stray account.
		assertThrows(InsufficientFundsException.class,
			() -> wallets.withdraw(GUILD, USER, "Ada", new BigDecimal("1000"), LedgerReason.BET_PLACED, null, null));

		assertTrue(wallets.find(GUILD, USER).isEmpty());
	}

	@Test
	void refusesNonPositiveMovements() {
		assertThrows(IllegalArgumentException.class,
			() -> wallets.deposit(GUILD, USER, "Ada", BigDecimal.ZERO, LedgerReason.ADMIN_GRANT, null, 1L));
		assertThrows(IllegalArgumentException.class,
			() -> wallets.withdraw(GUILD, USER, "Ada", new BigDecimal("-5"), LedgerReason.BET_PLACED, null, null));
		assertThrows(IllegalArgumentException.class,
			() -> wallets.adjust(GUILD, USER, "Ada", BigDecimal.ZERO, 1L));
	}

	@Test
	void adminAdjustmentMovesMoneyBothWays() {
		Wallet up = wallets.adjust(GUILD, USER, "Ada", new BigDecimal("15"), 42L);
		Wallet down = wallets.adjust(GUILD, USER, "Ada", new BigDecimal("-5"), 42L);

		assertEquals(new BigDecimal("115.00"), up.getBalance());
		assertEquals(new BigDecimal("110.00"), down.getBalance());
	}

	@Test
	void ledgerRecordsTheActorForAdminActions() {
		wallets.adjust(GUILD, USER, "Ada", new BigDecimal("15"), 42L);

		LedgerEntry entry = ledgerRepository
			.findByGuildIdAndUserIdOrderByCreatedAtDescIdDesc(GUILD, USER,
				org.springframework.data.domain.PageRequest.of(0, 1))
			.get(0);

		assertEquals(LedgerReason.ADMIN_GRANT, entry.getReason());
		assertEquals(42L, entry.getActorId());
		assertEquals(new BigDecimal("15.00"), entry.getDelta());
		assertEquals(new BigDecimal("115.00"), entry.getBalanceAfter());
	}

	@Test
	void walletsAreIsolatedPerGuild() {
		long otherGuild = 999_000_111L;
		wallets.getOrCreate(GUILD, USER, "Ada");
		Wallet inOtherGuild = wallets.getOrCreate(otherGuild, USER, "Ada");

		assertEquals(new BigDecimal("100.00"), inOtherGuild.getBalance());
		assertEquals(2, walletRepository.count());
	}

	@Test
	void totalInCirculationSumsEveryWallet() {
		wallets.getOrCreate(GUILD, USER, "Ada");
		wallets.getOrCreate(GUILD, OTHER_USER, "Grace");

		assertEquals(new BigDecimal("200.00"), wallets.totalInCirculation(GUILD));
	}

	@Test
	void dailyRewardCreditsOnceAndThenRespectsTheCooldown() {
		DailyRewardService.ClaimResult first = daily.claim(GUILD, USER, "Ada");
		DailyRewardService.ClaimResult second = daily.claim(GUILD, USER, "Ada");

		assertEquals(DailyRewardService.ClaimResult.Status.CLAIMED, first.status());
		assertEquals(new BigDecimal("125.00"), first.balance());
		assertEquals(DailyRewardService.ClaimResult.Status.ON_COOLDOWN, second.status());
		assertEquals(new BigDecimal("125.00"), second.balance());
		assertNotNull(second.nextEligibleAt());
	}

	@Test
	void dailyRewardIsDisabledWhenTheAmountIsZero() {
		appSettings.set(AppSettingKey.DAILY_AMOUNT, "0");

		DailyRewardService.ClaimResult result = daily.claim(GUILD, USER, "Ada");

		assertEquals(DailyRewardService.ClaimResult.Status.DISABLED, result.status());
		assertEquals(0, walletRepository.count(), "a disabled reward must not create wallets");
	}

	@Test
	void dailyClaimIsAtomicAndSurvivesConcurrentCalls() throws Exception {
		int threads = 8;
		Thread[] workers = new Thread[threads];
		int[] claimed = {0};

		for (int i = 0; i < threads; i++) {
			workers[i] = new Thread(() -> {
				DailyRewardService.ClaimResult result = daily.claim(GUILD, USER, "Ada");
				if (result.claimed()) {
					synchronized (claimed) {
						claimed[0]++;
					}
				}
			});
			workers[i].start();
		}
		for (Thread worker : workers) {
			worker.join(Duration.ofSeconds(20).toMillis());
		}

		assertEquals(1, claimed[0], "exactly one concurrent claim may be paid");
		assertEquals(new BigDecimal("125.00"), wallets.find(GUILD, USER).orElseThrow().getBalance());
	}

	@Test
	void versionColumnAdvancesOnEveryMutation() {
		Wallet created = wallets.getOrCreate(GUILD, USER, "Ada");
		long initialVersion = created.getVersion();

		wallets.deposit(GUILD, USER, "Ada", new BigDecimal("5"), LedgerReason.ADMIN_GRANT, null, 1L);

		long afterDeposit = wallets.find(GUILD, USER).orElseThrow().getVersion();
		assertTrue(afterDeposit > initialVersion, "optimistic locking version must change");
	}
}
