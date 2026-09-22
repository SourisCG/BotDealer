/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import souris.botdealer.domain.BetOption;

@Repository
public interface BetOptionRepository extends JpaRepository<BetOption, Long> {

	List<BetOption> findByEventIdOrderByOptionOrderAsc(long eventId);
}
