/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.music.engine;

/**
 * A track could not be resolved by any engine. Carries an i18n key plus arguments so the
 * Discord layer can explain the reason in the guild's language.
 */
public class ResolutionException extends RuntimeException {

	private final String messageKey;
	private final transient Object[] arguments;

	public ResolutionException(String messageKey, Object... arguments) {
		super(messageKey);
		this.messageKey = messageKey;
		this.arguments = arguments;
	}

	public ResolutionException(String messageKey, Throwable cause, Object... arguments) {
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
