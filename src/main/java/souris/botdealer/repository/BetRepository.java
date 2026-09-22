/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import souris.botdealer.domain.Bet;
import souris.botdealer.domain.BetStatus;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {

	List<Bet> findByEventIdOrderByPlacedAtAsc(long eventId);

	List<Bet> findByEventIdAndStatus(long eventId, BetStatus status);

	List<Bet> findByEventIdAndOptionId(long eventId, long optionId);

	List<Bet> findByGuildIdAndUserIdOrderByPlacedAtDesc(long guildId, long userId);

	long countByEventId(long eventId);

	long countByEventIdAndStatus(long eventId, BetStatus status);

	/**
	 * Total staked on one option. Refunded bets are excluded because their stake was
	 * returned; cancelled events therefore report an empty pool.
	 */
	@Query("select coalesce(sum(b.amount), 0) from Bet b where b.eventId = :eventId and b.optionId = :optionId"
		+ " and b.status <> :excluded")
	BigDecimal totalStakedOnOption(@Param("eventId") long eventId, @Param("optionId") long optionId,
		@Param("excluded") BetStatus excluded);

	/** Total pot of an event, the basis of every parimutuel calculation. */
	@Query("select coalesce(sum(b.amount), 0) from Bet b where b.eventId = :eventId and b.status <> :excluded")
	BigDecimal totalStakedOnEvent(@Param("eventId") long eventId, @Param("excluded") BetStatus excluded);
}
