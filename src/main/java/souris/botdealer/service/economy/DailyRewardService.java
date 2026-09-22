/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.economy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import souris.botdealer.domain.Wallet;

/**
 * Periodic top-up members can claim with a command.
 *
 * <p>Resolves the guild's amount and cooldown, then delegates the money movement to
 * {@link WalletService#claimDaily}, which performs the cooldown check and the credit in
 * one transaction so a claim can never be paid twice.</p>
 */
@Service
public class DailyRewardService {

	private static final Logger log = LoggerFactory.getLogger(DailyRewardService.class);

	/** Outcome of a claim attempt, ready to be rendered by the caller. */
	public record ClaimResult(Status status, BigDecimal amount, BigDecimal balance, Instant nextEligibleAt) {

		public enum Status {
			CLAIMED,
			ON_COOLDOWN,
			DISABLED
		}

		public boolean claimed() {
			return status == Status.CLAIMED;
		}
	}

	private final WalletService wallets;
	private final GuildConfigService guildConfig;

	public DailyRewardService(WalletService wallets, GuildConfigService guildConfig) {
		this.wallets = wallets;
		this.guildConfig = guildConfig;
	}

	public ClaimResult claim(long guildId, long userId, String displayName) {
		int amount = guildConfig.dailyAmount(guildId);
		int cooldownHours = guildConfig.dailyCooldownHours(guildId);
		BigDecimal reward = BigDecimal.valueOf(amount);

		if (amount <= 0 || cooldownHours <= 0) {
			BigDecimal balance = wallets.find(guildId, userId).map(Wallet::getBalance).orElse(BigDecimal.ZERO);
			return new ClaimResult(ClaimResult.Status.DISABLED, BigDecimal.ZERO, balance, null);
		}

		WalletService.DailyClaim claim = wallets.claimDaily(guildId, userId, displayName, reward,
			Duration.ofHours(cooldownHours));

		if (!claim.claimed()) {
			return new ClaimResult(ClaimResult.Status.ON_COOLDOWN, reward, claim.balance(),
				claim.nextEligibleAt());
		}
		log.debug("Daily reward of {} claimed by user {} in guild {}", amount, userId, guildId);
		return new ClaimResult(ClaimResult.Status.CLAIMED, reward, claim.balance(), claim.nextEligibleAt());
	}
}
