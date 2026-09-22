/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.service.tts;

/**
 * Speech could not be synthesized. Carries an i18n key so the Discord layer and the UI
 * can explain it in the user's language.
 */
public class TtsSynthesisException extends RuntimeException {

	private final String messageKey;
	private final transient Object[] arguments;

	public TtsSynthesisException(String messageKey, Object... arguments) {
		super(messageKey);
		this.messageKey = messageKey;
		this.arguments = arguments;
	}

	public TtsSynthesisException(String messageKey, Throwable cause, Object... arguments) {
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
