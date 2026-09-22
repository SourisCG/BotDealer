/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.betting;

/**
 * A bet or event action was rejected. Carries an i18n key plus arguments so the Discord
 * layer and the UI can render the reason in the user's language.
 */
public class BetValidationException extends RuntimeException {

	private final String messageKey;
	private final transient Object[] arguments;

	public BetValidationException(String messageKey, Object... arguments) {
		super(messageKey);
		this.messageKey = messageKey;
		this.arguments = arguments;
	}

	public String messageKey() {
		return messageKey;
	}

	public Object[] arguments() {
		return arguments.clone();
	}
}
