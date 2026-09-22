/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import souris.botdealer.domain.GuildConfig;

@Repository
public interface GuildConfigRepository extends JpaRepository<GuildConfig, Long> {
}
