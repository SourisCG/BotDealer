/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Key/value application setting.
 *
 * <p>Column names are explicit ({@code setting_key}/{@code setting_value}) because
 * {@code key} and {@code value} are reserved words in H2 and other databases. Secrets
 * are never stored here: the Discord token lives in the OS keychain.</p>
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AppSetting {

	@Id
	@Column(name = "setting_key", length = 120, nullable = false)
	private String key;

	@Column(name = "setting_value", length = 4000)
	private String value;
}
