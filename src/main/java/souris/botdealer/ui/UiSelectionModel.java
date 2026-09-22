/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui;

import java.util.List;
import java.util.OptionalLong;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.stereotype.Component;
import souris.botdealer.service.discord.DiscordBotService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;

/**
 * Guilds the bot is in, and which one the UI is currently looking at.
 *
 * <p>Shared so that switching from Events to Wallets keeps the same server selected, and
 * refreshed automatically when the bot connects or disconnects.</p>
 */
@Component
public class UiSelectionModel {

	/** One entry of the guild picker. */
	public record GuildChoice(long id, String name) {
		@Override
		public String toString() {
			return name;
		}
	}

	private final ObjectProperty<GuildChoice> selected = new SimpleObjectProperty<>();
	private final ObservableList<GuildChoice> guilds = FXCollections.observableArrayList();
	private final DiscordBotService bot;

	public UiSelectionModel(DiscordBotService bot, UiEventBus uiEvents) {
		this.bot = bot;
		uiEvents.subscribe(event -> {
			if (event instanceof UiEvents.BotStatusChanged) {
				refresh();
			}
		});
		refresh();
	}

	/** Rebuilds the list from the live session, keeping the current selection if possible. */
	public void refresh() {
		List<GuildChoice> choices = bot.guilds().stream()
			.map(guild -> new GuildChoice(guild.getIdLong(), guild.getName()))
			.sorted(java.util.Comparator.comparing(GuildChoice::name))
			.toList();
		Runnable update = () -> {
			GuildChoice previous = selected.get();
			guilds.setAll(choices);
			boolean stillThere = previous != null && choices.stream()
				.anyMatch(choice -> choice.id() == previous.id());
			if (!stillThere) {
				selected.set(choices.isEmpty() ? null : choices.get(0));
			}
		};
		if (Platform.isFxApplicationThread()) {
			update.run();
		} else {
			try {
				Platform.runLater(update);
			} catch (IllegalStateException e) {
				update.run();
			}
		}
	}

	public ObservableList<GuildChoice> guilds() {
		return guilds;
	}

	public ObjectProperty<GuildChoice> selectedProperty() {
		return selected;
	}

	public OptionalLong selectedGuildId() {
		GuildChoice choice = selected.get();
		return choice == null ? OptionalLong.empty() : OptionalLong.of(choice.id());
	}

	public boolean hasGuild() {
		return selected.get() != null;
	}
}
