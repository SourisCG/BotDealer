/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import souris.botdealer.domain.Wallet;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

	Optional<Wallet> findByGuildIdAndUserId(long guildId, long userId);

	List<Wallet> findByGuildIdOrderByBalanceDesc(long guildId);

	List<Wallet> findByGuildIdAndDisplayNameContainingIgnoreCaseOrderByBalanceDesc(long guildId, String name);

	long countByGuildId(long guildId);

	/** Total money in circulation for a guild; used by the dashboard. */
	@Query("select coalesce(sum(w.balance), 0) from Wallet w where w.guildId = :guildId")
	BigDecimal totalBalance(@Param("guildId") long guildId);

	/** Top wallets for a leaderboard-style view. */
	@Query("select w from Wallet w where w.guildId = :guildId order by w.balance desc")
	List<Wallet> topWallets(@Param("guildId") long guildId, Pageable pageable);
}
