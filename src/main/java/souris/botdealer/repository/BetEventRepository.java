/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import souris.botdealer.domain.BetEvent;
import souris.botdealer.domain.EventStatus;

@Repository
public interface BetEventRepository extends JpaRepository<BetEvent, Long> {

	List<BetEvent> findByGuildIdOrderByCreatedAtDesc(long guildId);

	List<BetEvent> findAllByOrderByCreatedAtDesc();

	List<BetEvent> findByStatusOrderByCreatedAtAsc(EventStatus status);

	/** Open events whose betting window has elapsed; the bot closes these automatically. */
	List<BetEvent> findByStatusAndClosesAtBefore(EventStatus status, Instant instant);

	long countByGuildIdAndStatus(long guildId, EventStatus status);
}
