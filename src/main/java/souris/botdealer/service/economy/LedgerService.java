/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.economy;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import souris.botdealer.domain.LedgerEntry;
import souris.botdealer.domain.LedgerReason;
import souris.botdealer.repository.LedgerEntryRepository;

/**
 * Append-only history of every balance movement.
 *
 * <p>{@link #record} deliberately has no transaction annotation: it must always run
 * inside the transaction that moved the money, so a rollback removes both the balance
 * change and its ledger row.</p>
 */
@Service
public class LedgerService {

	private final LedgerEntryRepository repository;

	public LedgerService(LedgerEntryRepository repository) {
		this.repository = repository;
	}

	/** Writes one entry. Must be called inside the transaction that changed the balance. */
	public LedgerEntry record(long guildId, long userId, BigDecimal delta, BigDecimal balanceAfter,
			LedgerReason reason, Long referenceId, Long actorId) {
		LedgerEntry entry = new LedgerEntry(null, guildId, userId, delta, balanceAfter, reason,
			referenceId, actorId, java.time.Instant.now());
		return repository.save(entry);
	}

	@Transactional(readOnly = true)
	public List<LedgerEntry> history(long guildId, long userId, int limit) {
		return repository.findByGuildIdAndUserIdOrderByCreatedAtDescIdDesc(guildId, userId,
			PageRequest.of(0, Math.max(1, limit)));
	}

	@Transactional(readOnly = true)
	public List<LedgerEntry> recentForGuild(long guildId, int limit) {
		return repository.findByGuildIdOrderByCreatedAtDescIdDesc(guildId, PageRequest.of(0, Math.max(1, limit)));
	}
}
