/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.discord;

/**
 * The bot could not start. Carries an i18n key plus arguments so the UI can explain the
 * problem in the user's language; never carries the token.
 */
public class BotStartException extends RuntimeException {

	private final String messageKey;
	private final transient Object[] arguments;

	public BotStartException(String messageKey, Object... arguments) {
		super(messageKey);
		this.messageKey = messageKey;
		this.arguments = arguments;
	}

	public BotStartException(String messageKey, Throwable cause, Object... arguments) {
		super(messageKey, cause);
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
