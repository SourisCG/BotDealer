/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.betting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import souris.botdealer.domain.BetEvent;
import souris.botdealer.domain.BetStatus;
import souris.botdealer.domain.EventStatus;
import souris.botdealer.domain.LedgerReason;
import souris.botdealer.domain.PayoutMode;
import souris.botdealer.domain.Wallet;
import souris.botdealer.repository.AppSettingRepository;
import souris.botdealer.repository.BetEventRepository;
import souris.botdealer.repository.BetOptionRepository;
import souris.botdealer.repository.BetRepository;
import souris.botdealer.repository.GuildConfigRepository;
import souris.botdealer.repository.LedgerEntryRepository;
import souris.botdealer.repository.WalletRepository;
import souris.botdealer.service.economy.InsufficientFundsException;
import souris.botdealer.service.economy.WalletService;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BettingLifecycleTest {

	private static final long GUILD = 555_000_111L;
	private static final long ALICE = 1_000_001L;
	private static final long BOB = 1_000_002L;
	private static final long CAROL = 1_000_003L;
	private static final long ADMIN = 1_000_004L;

	@Autowired
	private EventService events;
	@Autowired
	private BetService bets;
	@Autowired
	private EventQueryService queries;
	@Autowired
	private WalletService wallets;
	@Autowired
	private AppSettingsService appSettings;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private LedgerEntryRepository ledgerRepository;
	@Autowired
	private BetRepository betRepository;
	@Autowired
	private BetEventRepository eventRepository;
	@Autowired
	private BetOptionRepository betOptionRepository;
	@Autowired
	private GuildConfigRepository guildConfigRepository;
	@Autowired
	private AppSettingRepository appSettingRepository;

	@BeforeEach
	void cleanSlate() {
		ledgerRepository.deleteAllInBatch();
		betRepository.deleteAllInBatch();
		betOptionRepository.deleteAllInBatch();
		eventRepository.deleteAllInBatch();
		walletRepository.deleteAllInBatch();
		guildConfigRepository.deleteAllInBatch();
		appSettingRepository.deleteAllInBatch();
		// Every test starts with 100 per member and no daily income to keep math obvious.
		appSettings.set(AppSettingKey.DAILY_AMOUNT, "0");
	}

	private BetEvent parimutuelEvent(String... labels) {
		return events.create(new EventService.CreateRequest(GUILD, ADMIN, "Final", "who wins",
			PayoutMode.PARIMUTUEL, null, BigDecimal.ZERO, null, EventService.drafts(labels), true));
	}

	private BetEvent fixedOddsEvent() {
		return events.create(new EventService.CreateRequest(GUILD, ADMIN, "Derby", "",
			PayoutMode.FIXED_ODDS, null, BigDecimal.ZERO, null,
			List.of(new EventService.OptionDraft("Red", new BigDecimal("2.00")),
				new EventService.OptionDraft("Blue", new BigDecimal("3.00"))),
			true));
	}

	private long optionId(BetEvent event, int index) {
		return event.getOptions().get(index).getId();
	}

	private BigDecimal balance(long userId) {
		return wallets.find(GUILD, userId).map(Wallet::getBalance).orElse(BigDecimal.ZERO);
	}

	// ------------------------------------------------------------------ placement

	@Test
	void placingABetMovesMoneyAndRecordsTheStake() {
		BetEvent event = parimutuelEvent("A", "B");

		BetService.PlacedBet placed = bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0),
			new BigDecimal("30"));

		assertEquals(new BigDecimal("70.00"), placed.balanceAfter());
		assertEquals(new BigDecimal("30.00"), placed.amount());
		assertEquals(1, betRepository.countByEventId(event.getId()));
	}

	@Test
	void placingABetWithoutFundsChangesNothing() {
		BetEvent event = parimutuelEvent("A", "B");

		assertThrows(InsufficientFundsException.class, () -> bets.place(GUILD, ALICE, "Alice", event.getId(),
			optionId(event, 0), new BigDecimal("500")));

		assertEquals(BigDecimal.ZERO, balance(ALICE), "no wallet should have been created");
		assertEquals(0, betRepository.countByEventId(event.getId()));
	}

	@Test
	void closedEventsRefuseBets() {
		BetEvent event = parimutuelEvent("A", "B");
		events.close(event.getId());

		BetValidationException failure = assertThrows(BetValidationException.class,
			() -> bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("10")));

		assertEquals("bet.error.notOpen", failure.messageKey());
		assertEquals(BigDecimal.ZERO, balance(ALICE));
	}

	@Test
	void expiredClosingTimeRefusesBets() {
		BetEvent event = parimutuelEvent("A", "B");
		backdateClosingTime(event);

		BetValidationException failure = assertThrows(BetValidationException.class,
			() -> bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("10")));

		assertEquals("bet.error.closed", failure.messageKey());
	}

	@Test
	void betsOnAnotherEventOptionAreRejected() {
		BetEvent first = parimutuelEvent("A", "B");
		BetEvent second = parimutuelEvent("C", "D");

		BetValidationException failure = assertThrows(BetValidationException.class,
			() -> bets.place(GUILD, ALICE, "Alice", first.getId(), optionId(second, 0), new BigDecimal("10")));

		assertEquals("bet.error.optionNotFound", failure.messageKey());
	}

	@Test
	void betsFromAnotherGuildAreRejected() {
		BetEvent event = parimutuelEvent("A", "B");

		BetValidationException failure = assertThrows(BetValidationException.class,
			() -> bets.place(GUILD + 1, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("10")));

		assertEquals("bet.error.wrongGuild", failure.messageKey());
	}

	@Test
	void minimumAndMaximumBetsAreEnforced() {
		var config = guildConfigRepository.findById(GUILD).orElseGet(() -> {
			var created = new souris.botdealer.domain.GuildConfig();
			created.setGuildId(GUILD);
			return created;
		});
		config.setMinBet(new BigDecimal("5"));
		config.setMaxBet(new BigDecimal("50"));
		guildConfigRepository.save(config);
		BetEvent event = parimutuelEvent("A", "B");

		BetValidationException tooSmall = assertThrows(BetValidationException.class,
			() -> bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("4")));
		assertEquals("bet.error.belowMinimum", tooSmall.messageKey());

		BetValidationException tooBig = assertThrows(BetValidationException.class,
			() -> bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("51")));
		assertEquals("bet.error.aboveMaximum", tooBig.messageKey());
	}

	@Test
	void zeroAndNegativeBetsAreRejected() {
		BetEvent event = parimutuelEvent("A", "B");

		assertEquals("bet.error.amountPositive", assertThrows(BetValidationException.class,
			() -> bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), BigDecimal.ZERO))
			.messageKey());
		assertEquals("bet.error.amountPositive", assertThrows(BetValidationException.class,
			() -> bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("-5")))
			.messageKey());
	}

	@Test
	void fixedOddsAreFrozenAtPlacement() {
		BetEvent event = fixedOddsEvent();

		BetService.PlacedBet placed = bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0),
			new BigDecimal("10"));

		assertEquals(new BigDecimal("2.00"), placed.oddsAtPlacement());
	}

	// ------------------------------------------------------------------ parimutuel settlement

	@Test
	void parimutuelSettlementPaysTheWinnersFromThePool() {
		BetEvent event = parimutuelEvent("A", "B");
		long optionA = optionId(event, 0);
		long optionB = optionId(event, 1);
		bets.place(GUILD, ALICE, "Alice", event.getId(), optionA, new BigDecimal("30"));
		bets.place(GUILD, BOB, "Bob", event.getId(), optionB, new BigDecimal("10"));

		EventService.SettlementReport report = events.settle(event.getId(), optionA);

		assertEquals("settle.result.paid", report.outcomeKey());
		assertEquals(1, report.winnerCount());
		assertEquals(1, report.loserCount());
		assertEquals(new BigDecimal("40.00"), report.totalPool());
		assertEquals(new BigDecimal("40.00"), report.totalPaid());
		// Alice staked 30 and gets the whole 40 pot back
		assertEquals(new BigDecimal("110.00"), balance(ALICE));
		// Bob staked 10 and lost it
		assertEquals(new BigDecimal("90.00"), balance(BOB));
		assertEquals(EventStatus.SETTLED, eventRepository.findById(event.getId()).orElseThrow().getStatus());
		assertEquals(optionA, eventRepository.findById(event.getId()).orElseThrow().getWinningOptionId());
	}

	@Test
	void parimutuelSplitsBetweenMultipleWinners() {
		BetEvent event = parimutuelEvent("A", "B");
		long optionA = optionId(event, 0);
		bets.place(GUILD, ALICE, "Alice", event.getId(), optionA, new BigDecimal("30"));
		bets.place(GUILD, BOB, "Bob", event.getId(), optionA, new BigDecimal("10"));
		bets.place(GUILD, CAROL, "Carol", event.getId(), optionId(event, 1), new BigDecimal("60"));

		EventService.SettlementReport report = events.settle(event.getId(), optionA);

		assertEquals(2, report.winnerCount());
		assertEquals(new BigDecimal("100.00"), report.totalPaid());
		// 100 pot, winners staked 30 and 10 -> 75 and 25
		assertEquals(new BigDecimal("145.00"), balance(ALICE));
		assertEquals(new BigDecimal("115.00"), balance(BOB)); // 100 - 10 stake + 25 payout
		assertEquals(new BigDecimal("40.00"), balance(CAROL));
	}

	@Test
	void rakeIsCreditedToTheHouseWallet() {
		BetEvent event = events.create(new EventService.CreateRequest(GUILD, ADMIN, "Raked", "",
			PayoutMode.PARIMUTUEL, null, new BigDecimal("10"), null, EventService.drafts("A", "B"), true));
		long optionA = optionId(event, 0);
		bets.place(GUILD, ALICE, "Alice", event.getId(), optionA, new BigDecimal("50"));
		bets.place(GUILD, BOB, "Bob", event.getId(), optionId(event, 1), new BigDecimal("50"));

		EventService.SettlementReport report = events.settle(event.getId(), optionA);

		assertEquals(new BigDecimal("10.00"), report.rakeCollected());
		assertEquals(new BigDecimal("90.00"), report.totalPaid());
		assertEquals(new BigDecimal("140.00"), balance(ALICE)); // 100 - 50 stake + 90 payout
		assertEquals(new BigDecimal("10.00"), balance(WalletService.HOUSE_USER_ID),
			"the house wallet starts at zero and only holds the rake");
	}

	@Test
	void settlementRefundsEveryoneWhenNobodyBackedTheWinner() {
		BetEvent event = parimutuelEvent("A", "B");
		long optionB = optionId(event, 1);
		bets.place(GUILD, ALICE, "Alice", event.getId(), optionB, new BigDecimal("20"));

		EventService.SettlementReport report = events.settle(event.getId(), optionId(event, 0));

		assertTrue(report.refundedInstead());
		assertEquals("settle.result.refundedNoWinners", report.outcomeKey());
		assertEquals(new BigDecimal("100.00"), balance(ALICE), "the stake must come back");
		assertEquals(BetStatus.REFUNDED,
			betRepository.findByEventIdAndStatus(event.getId(), BetStatus.REFUNDED).get(0).getStatus());
	}

	@Test
	void settlingWithoutBetsMovesNoMoney() {
		BetEvent event = parimutuelEvent("A", "B");

		EventService.SettlementReport report = events.settle(event.getId(), optionId(event, 0));

		assertEquals("settle.result.noBets", report.outcomeKey());
		assertEquals(0, walletRepository.count());
		assertEquals(EventStatus.SETTLED, eventRepository.findById(event.getId()).orElseThrow().getStatus());
	}

	@Test
	void anEventCannotBeSettledTwice() {
		BetEvent event = parimutuelEvent("A", "B");
		events.settle(event.getId(), optionId(event, 0));

		BetValidationException failure = assertThrows(BetValidationException.class,
			() -> events.settle(event.getId(), optionId(event, 1)));

		assertEquals("event.error.alreadyFinal", failure.messageKey());
	}

	// ------------------------------------------------------------------ fixed odds settlement

	@Test
	void fixedOddsPayTheFrozenMultiplier() {
		BetEvent event = fixedOddsEvent();
		long red = optionId(event, 0);
		bets.place(GUILD, ALICE, "Alice", event.getId(), red, new BigDecimal("10"));
		bets.place(GUILD, BOB, "Bob", event.getId(), optionId(event, 1), new BigDecimal("10"));

		EventService.SettlementReport report = events.settle(event.getId(), red);

		// 10 staked at 2.00 -> 20 back
		assertEquals(new BigDecimal("20.00"), report.totalPaid());
		assertEquals(new BigDecimal("110.00"), balance(ALICE));
		assertEquals(new BigDecimal("90.00"), balance(BOB));
		assertEquals(0, report.rakeCollected().compareTo(BigDecimal.ZERO),
			"fixed odds carry no extra rake");
	}

	@Test
	void fixedOddsCanPayMoreThanThePool() {
		// The creator carries the risk: total staked is 20 but the payout is 30.
		BetEvent event = fixedOddsEvent();
		long blue = optionId(event, 1);
		bets.place(GUILD, ALICE, "Alice", event.getId(), blue, new BigDecimal("10"));
		bets.place(GUILD, BOB, "Bob", event.getId(), optionId(event, 0), new BigDecimal("10"));

		EventService.SettlementReport report = events.settle(event.getId(), blue);

		assertEquals(new BigDecimal("30.00"), report.totalPaid());
		assertEquals(new BigDecimal("120.00"), balance(ALICE));
	}

	// ------------------------------------------------------------------ cancel

	@Test
	void cancellingRefundsEveryStake() {
		BetEvent event = parimutuelEvent("A", "B");
		bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("25"));
		bets.place(GUILD, BOB, "Bob", event.getId(), optionId(event, 1), new BigDecimal("40"));

		EventService.SettlementReport report = events.cancel(event.getId());

		assertEquals("settle.result.cancelled", report.outcomeKey());
		assertEquals(new BigDecimal("100.00"), balance(ALICE));
		assertEquals(new BigDecimal("100.00"), balance(BOB));
		assertEquals(EventStatus.CANCELLED, eventRepository.findById(event.getId()).orElseThrow().getStatus());
	}

	@Test
	void aCancelledEventCannotBeSettled() {
		BetEvent event = parimutuelEvent("A", "B");
		events.cancel(event.getId());

		assertEquals("event.error.alreadyFinal", assertThrows(BetValidationException.class,
			() -> events.settle(event.getId(), optionId(event, 0))).messageKey());
	}

	// ------------------------------------------------------------------ lifecycle + views

	@Test
	void draftEventsDoNotAcceptBetsUntilOpened() {
		BetEvent draft = events.create(new EventService.CreateRequest(GUILD, ADMIN, "Soon", "",
			PayoutMode.PARIMUTUEL, null, BigDecimal.ZERO, null, EventService.drafts("A", "B"), false));

		assertEquals(EventStatus.DRAFT, draft.getStatus());
		assertEquals("bet.error.notOpen", assertThrows(BetValidationException.class,
			() -> bets.place(GUILD, ALICE, "Alice", draft.getId(), optionId(draft, 0), new BigDecimal("10")))
			.messageKey());

		events.open(draft.getId());
		assertEquals(EventStatus.OPEN, eventRepository.findById(draft.getId()).orElseThrow().getStatus());
	}

	@Test
	void creatingAnEventValidatesItsOptions() {
		assertEquals("event.error.needsOptions", assertThrows(BetValidationException.class,
			() -> events.create(new EventService.CreateRequest(GUILD, ADMIN, "T", "", PayoutMode.PARIMUTUEL,
				null, BigDecimal.ZERO, null, List.of(new EventService.OptionDraft("Only", null)), true)))
			.messageKey());

		assertEquals("event.error.titleRequired", assertThrows(BetValidationException.class,
			() -> events.create(new EventService.CreateRequest(GUILD, ADMIN, "  ", "",
				PayoutMode.PARIMUTUEL, null, BigDecimal.ZERO, null, EventService.drafts("A", "B"), true)))
			.messageKey());

		assertEquals("event.error.oddsTooLow", assertThrows(BetValidationException.class,
			() -> events.create(new EventService.CreateRequest(GUILD, ADMIN, "T", "", PayoutMode.FIXED_ODDS,
				null, BigDecimal.ZERO, null,
				List.of(new EventService.OptionDraft("A", new BigDecimal("0.50")),
					new EventService.OptionDraft("B", new BigDecimal("2.00"))), true)))
			.messageKey());

		assertEquals("event.error.closeInPast", assertThrows(BetValidationException.class,
			() -> events.create(new EventService.CreateRequest(GUILD, ADMIN, "T", "", PayoutMode.PARIMUTUEL,
				Instant.now().minusSeconds(30), BigDecimal.ZERO, null, EventService.drafts("A", "B"), true)))
			.messageKey());
	}

	@Test
	void expiredEventsAreClosedAutomatically() {
		events.create(new EventService.CreateRequest(GUILD, ADMIN, "Future", "", PayoutMode.PARIMUTUEL,
			Instant.now().plusSeconds(3600), BigDecimal.ZERO, null, EventService.drafts("A", "B"), true));
		BetEvent expired = parimutuelEvent("A", "B");
		backdateClosingTime(expired);

		assertEquals(List.of(expired.getId()), events.closeExpired(),
			"only the backdated event should close");
		assertEquals(EventStatus.CLOSED, eventRepository.findById(expired.getId()).orElseThrow().getStatus());
	}

	/** Moves an already-open event's closing time into the past, as time passing would. */
	private void backdateClosingTime(BetEvent event) {
		BetEvent stored = eventRepository.findById(event.getId()).orElseThrow();
		stored.setClosesAt(Instant.now().minusSeconds(1));
		eventRepository.save(stored);
	}

	@Test
	void theViewReportsPoolsAndImpliedOdds() {
		BetEvent event = parimutuelEvent("A", "B");
		long optionA = optionId(event, 0);
		bets.place(GUILD, ALICE, "Alice", event.getId(), optionA, new BigDecimal("25"));
		bets.place(GUILD, BOB, "Bob", event.getId(), optionId(event, 1), new BigDecimal("75"));

		EventQueryService.EventView view = queries.view(event.getId()).orElseThrow();

		assertEquals(new BigDecimal("100.00"), view.totalPool());
		assertEquals(2, view.betCount());
		assertEquals(new BigDecimal("25.00"), view.options().get(0).pool());
		assertEquals(1, view.options().get(0).betCount());
		assertEquals(new BigDecimal("4.00"), view.options().get(0).impliedOdds());
		assertEquals(new BigDecimal("1.33"), view.options().get(1).impliedOdds());
	}

	@Test
	void theViewWarnsAboutFixedOddsLiability() {
		BetEvent event = fixedOddsEvent();
		bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("10"));

		var liabilities = queries.fixedOddsLiabilities(event);

		assertEquals(new BigDecimal("20.00"), liabilities.get(optionId(event, 0)));
		assertEquals(new BigDecimal("0.00"), liabilities.get(optionId(event, 1)));
	}

	@Test
	void betsAreListedForAMember() {
		BetEvent event = parimutuelEvent("A", "B");
		bets.place(GUILD, ALICE, "Alice", event.getId(), optionId(event, 0), new BigDecimal("10"));

		List<souris.botdealer.domain.Bet> mine = bets.betsOf(GUILD, ALICE);

		assertEquals(1, mine.size());
		assertEquals(event.getId(), mine.get(0).getEventId());
		assertEquals(BetStatus.PLACED, mine.get(0).getStatus());
		assertTrue(ledgerRepository.findByGuildIdAndUserIdOrderByCreatedAtDescIdDesc(GUILD, ALICE,
			org.springframework.data.domain.PageRequest.of(0, 10)).stream()
			.anyMatch(entry -> entry.getReason() == LedgerReason.BET_PLACED));
		assertNotNull(mine.get(0).getPlacedAt());
	}
}
