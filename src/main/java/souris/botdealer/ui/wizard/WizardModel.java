/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.wizard;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Component;
import souris.botdealer.security.TokenValidation;
import souris.botdealer.settings.MusicEngine;

/**
 * Mutable state collected by the setup wizard.
 *
 * <p>Lives as a singleton bean so every step controller reads and writes the same
 * instance; the wizard is single-threaded on the JavaFX thread, so plain fields are
 * enough (no JavaFX properties needed).</p>
 */
@Component
public class WizardModel {

	/** Current step index, kept here so a language reload resumes where the user was. */
	private int step;

	private Locale language;
	private String token = "";
	private TokenValidation tokenValidation;
	private boolean skipToken;

	private String currencySingular = "chorizo";
	private String currencyPlural = "chorizos";
	private String currencySymbol = "\uD83C\uDF2D";
	private BigDecimal startingBalance = new BigDecimal("100");
	private int dailyAmount = 25;
	private int dailyCooldownHours = 24;

	private MusicEngine musicEngine = MusicEngine.AUTO;

	private boolean ttsEnabled = true;
	private String ttsVoice = "";
	private double ttsSpeed = 1.0;

	public int getStep() {
		return step;
	}

	public void setStep(int step) {
		this.step = step;
	}

	public Locale getLanguage() {
		return language;
	}

	public void setLanguage(Locale language) {
		this.language = language;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token == null ? "" : token;
	}

	public TokenValidation getTokenValidation() {
		return tokenValidation;
	}

	public void setTokenValidation(TokenValidation tokenValidation) {
		this.tokenValidation = tokenValidation;
	}

	/** True when the user chose to configure the bot later. */
	public boolean isSkipToken() {
		return skipToken;
	}

	public void setSkipToken(boolean skipToken) {
		this.skipToken = skipToken;
	}

	/** A token is usable when Discord confirmed it during this wizard run. */
	public boolean hasValidatedToken() {
		return tokenValidation != null && tokenValidation.isValid();
	}

	public String getCurrencySingular() {
		return currencySingular;
	}

	public void setCurrencySingular(String currencySingular) {
		this.currencySingular = currencySingular;
	}

	public String getCurrencyPlural() {
		return currencyPlural;
	}

	public void setCurrencyPlural(String currencyPlural) {
		this.currencyPlural = currencyPlural;
	}

	public String getCurrencySymbol() {
		return currencySymbol;
	}

	public void setCurrencySymbol(String currencySymbol) {
		this.currencySymbol = currencySymbol;
	}

	public BigDecimal getStartingBalance() {
		return startingBalance;
	}

	public void setStartingBalance(BigDecimal startingBalance) {
		this.startingBalance = startingBalance;
	}

	public int getDailyAmount() {
		return dailyAmount;
	}

	public void setDailyAmount(int dailyAmount) {
		this.dailyAmount = dailyAmount;
	}

	public int getDailyCooldownHours() {
		return dailyCooldownHours;
	}

	public void setDailyCooldownHours(int dailyCooldownHours) {
		this.dailyCooldownHours = dailyCooldownHours;
	}

	public MusicEngine getMusicEngine() {
		return musicEngine;
	}

	public void setMusicEngine(MusicEngine musicEngine) {
		this.musicEngine = musicEngine;
	}

	public boolean isTtsEnabled() {
		return ttsEnabled;
	}

	public void setTtsEnabled(boolean ttsEnabled) {
		this.ttsEnabled = ttsEnabled;
	}

	public String getTtsVoice() {
		return ttsVoice;
	}

	public void setTtsVoice(String ttsVoice) {
		this.ttsVoice = ttsVoice == null ? "" : ttsVoice;
	}

	public double getTtsSpeed() {
		return ttsSpeed;
	}

	public void setTtsSpeed(double ttsSpeed) {
		this.ttsSpeed = ttsSpeed;
	}

	/** Resets every choice so a restart of the wizard starts clean. */
	public void reset() {
		step = 0;
		language = null;
		token = "";
		tokenValidation = null;
		skipToken = false;
		currencySingular = "chorizo";
		currencyPlural = "chorizos";
		currencySymbol = "\uD83C\uDF2D";
		startingBalance = new BigDecimal("100");
		dailyAmount = 25;
		dailyCooldownHours = 24;
		musicEngine = MusicEngine.AUTO;
		ttsEnabled = true;
		ttsVoice = "";
		ttsSpeed = 1.0;
	}
}
