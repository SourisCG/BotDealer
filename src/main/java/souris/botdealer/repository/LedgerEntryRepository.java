/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import souris.botdealer.domain.LedgerEntry;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	List<LedgerEntry> findByGuildIdAndUserIdOrderByCreatedAtDescIdDesc(long guildId, long userId, Pageable page);

	List<LedgerEntry> findByGuildIdOrderByCreatedAtDescIdDesc(long guildId, Pageable page);
}
