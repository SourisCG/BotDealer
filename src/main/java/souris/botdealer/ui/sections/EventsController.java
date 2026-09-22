/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.domain.BetEvent;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.betting.BetValidationException;
import souris.botdealer.service.betting.EventQueryService;
import souris.botdealer.service.betting.EventService;
import souris.botdealer.service.discord.EventAnnouncer;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;
import souris.botdealer.service.economy.GuildConfigService;
import souris.botdealer.ui.FxViewLoader;
import souris.botdealer.ui.dialogs.EventEditorController;
import souris.botdealer.ui.UiSelectionModel;
import souris.botdealer.ui.UiUtils;
import souris.botdealer.util.Money;

/**
 * Events screen: the list on the left, the selected event (options, pools, actions) on
 * the right.
 *
 * <p>The GUI is the primary way to manage events; the slash commands mirror only the
 * common cases.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EventsController {

	private static final DateTimeFormatter CLOSE_FORMAT =
		DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

	@FXML
	private Label titleLabel;
	@FXML
	private ComboBox<UiSelectionModel.GuildChoice> guildCombo;
	@FXML
	private Button refreshButton;
	@FXML
	private Button newEventButton;
	@FXML
	private TableView<EventQueryService.EventView> eventTable;
	@FXML
	private TableColumn<EventQueryService.EventView, String> idColumn;
	@FXML
	private TableColumn<EventQueryService.EventView, String> titleColumn;
	@FXML
	private TableColumn<EventQueryService.EventView, String> modeColumn;
	@FXML
	private TableColumn<EventQueryService.EventView, String> statusColumn;
	@FXML
	private TableColumn<EventQueryService.EventView, String> poolColumn;
	@FXML
	private TableColumn<EventQueryService.EventView, String> betsColumn;
	@FXML
	private TableColumn<EventQueryService.EventView, String> closesColumn;
	@FXML
	private Label detailTitle;
	@FXML
	private Label detailSubtitle;
	@FXML
	private TableView<EventQueryService.OptionView> optionTable;
	@FXML
	private TableColumn<EventQueryService.OptionView, String> optionIdColumn;
	@FXML
	private TableColumn<EventQueryService.OptionView, String> optionLabelColumn;
	@FXML
	private TableColumn<EventQueryService.OptionView, String> optionPoolColumn;
	@FXML
	private TableColumn<EventQueryService.OptionView, String> optionOddsColumn;
	@FXML
	private Button openButton;
	@FXML
	private Button closeButton;
	@FXML
	private Button settleButton;
	@FXML
	private Button cancelButton;
	@FXML
	private Label statusLabel;

	private final I18nService i18n;
	private final EventQueryService queries;
	private final EventService events;
	private final EventAnnouncer announcer;
	private final GuildConfigService guildConfig;
	private final UiSelectionModel selection;
	private final FxViewLoader viewLoader;

	public EventsController(I18nService i18n, EventQueryService queries, EventService events,
			EventAnnouncer announcer, GuildConfigService guildConfig, UiSelectionModel selection,
			FxViewLoader viewLoader, UiEventBus uiEvents) {
		this.i18n = i18n;
		this.queries = queries;
		this.events = events;
		this.announcer = announcer;
		this.guildConfig = guildConfig;
		this.selection = selection;
		this.viewLoader = viewLoader;
		uiEvents.subscribe(event -> {
			if (event instanceof UiEvents.EventChanged) {
				refresh();
			}
		});
	}

	public void initialize() {
		titleLabel.setText(i18n.get("sections.events.title"));
		refreshButton.setText(i18n.get("events.refresh"));
		newEventButton.setText(i18n.get("events.new"));
		openButton.setText(i18n.get("events.action.open"));
		closeButton.setText(i18n.get("events.action.close"));
		settleButton.setText(i18n.get("events.action.settle"));
		cancelButton.setText(i18n.get("events.action.cancel"));

		idColumn.setCellValueFactory(cell -> new SimpleStringProperty("#" + cell.getValue().eventId()));
		titleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().title()));
		modeColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(i18n.get(cell.getValue().payoutMode().labelKey())));
		statusColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(i18n.get(cell.getValue().status().labelKey())));
		poolColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(cell.getValue().totalPool().toPlainString()));
		betsColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(Long.toString(cell.getValue().betCount())));
		closesColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().closesAt() == null
			? i18n.get("events.noClose")
			: CLOSE_FORMAT.format(cell.getValue().closesAt())));

		optionIdColumn.setCellValueFactory(cell ->
			new SimpleStringProperty("#" + cell.getValue().optionId()));
		optionLabelColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().label()));
		optionPoolColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(cell.getValue().pool().toPlainString()));
		optionOddsColumn.setCellValueFactory(cell -> new SimpleStringProperty(
			cell.getValue().impliedOdds() == null ? "-" : "x" + cell.getValue().impliedOdds().toPlainString()));

		guildCombo.setItems(selection.guilds());
		guildCombo.valueProperty().bindBidirectional(selection.selectedProperty());
		guildCombo.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
		eventTable.getSelectionModel().selectedItemProperty()
			.addListener((observable, oldValue, newValue) -> showDetail(newValue));
		refresh();
	}

	@FXML
	private void refresh() {
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			var views = queries.listByGuild(guildId).stream().map(queries::toView).toList();
			eventTable.setItems(FXCollections.observableArrayList(views));
			if (!views.isEmpty() && eventTable.getSelectionModel().getSelectedItem() == null) {
				eventTable.getSelectionModel().selectFirst();
			}
			statusLabel.setText("");
		}, () -> {
			eventTable.setItems(FXCollections.observableArrayList());
			detailTitle.setText(i18n.get("dashboard.noGuild"));
			detailSubtitle.setText("");
			optionTable.setItems(FXCollections.observableArrayList());
		});
	}

	private void showDetail(EventQueryService.EventView view) {
		if (view == null) {
			detailTitle.setText(i18n.get("events.detail.none"));
			detailSubtitle.setText("");
			optionTable.setItems(FXCollections.observableArrayList());
			return;
		}
		detailTitle.setText("#" + view.eventId() + " · " + view.title());
		detailSubtitle.setText(i18n.get("events.detail.subtitle",
			i18n.get(view.status().labelKey()), i18n.get(view.payoutMode().labelKey()),
			view.totalPool().toPlainString(), view.betCount()));
		optionTable.setItems(FXCollections.observableArrayList(view.options()));
		boolean finalState = view.status().isFinal();
		openButton.setDisable(view.status() != souris.botdealer.domain.EventStatus.DRAFT);
		closeButton.setDisable(view.status() != souris.botdealer.domain.EventStatus.OPEN);
		settleButton.setDisable(finalState);
		cancelButton.setDisable(finalState);
	}

	@FXML
	private void newEvent() {
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			Optional<EventService.CreateRequest> request = newEditor().show(window(), guildId);
			request.ifPresent(createRequest -> {
				try {
					BetEvent created = events.create(createRequest);
					announcer.refresh(created.getId());
					statusLabel.setText(i18n.get("events.created", "#" + created.getId()));
					refresh();
				} catch (BetValidationException e) {
					statusLabel.setText(i18n.get(e.messageKey(), e.arguments()));
				}
			});
		}, () -> statusLabel.setText(i18n.get("dashboard.noGuild")));
	}

	@FXML
	private void openEvent() {
		actOnSelected(eventId -> events.open(eventId), "events.opened");
	}

	@FXML
	private void closeEvent() {
		actOnSelected(eventId -> events.close(eventId), "events.closed");
	}

	@FXML
	private void cancelEvent() {
		EventQueryService.EventView view = eventTable.getSelectionModel().getSelectedItem();
		if (view == null) {
			return;
		}
		if (!UiUtils.confirm(i18n.get("events.confirm.cancel.title"),
			i18n.get("events.confirm.cancel.header"), i18n.get("events.confirm.cancel.body"))) {
			return;
		}
		try {
			EventService.SettlementReport report = events.cancel(view.eventId());
			statusLabel.setText(i18n.get("events.cancelled", report.totalPaid().toPlainString()));
			announcer.refresh(view.eventId());
			refresh();
		} catch (BetValidationException e) {
			statusLabel.setText(i18n.get(e.messageKey(), e.arguments()));
		}
	}

	@FXML
	private void settleEvent() {
		EventQueryService.EventView view = eventTable.getSelectionModel().getSelectedItem();
		EventQueryService.OptionView option = optionTable.getSelectionModel().getSelectedItem();
		if (view == null) {
			return;
		}
		if (option == null) {
			statusLabel.setText(i18n.get("events.error.pickWinner"));
			return;
		}
		if (!UiUtils.confirm(i18n.get("events.confirm.settle.title"),
			i18n.get("events.confirm.settle.header", option.label()),
			i18n.get("events.confirm.settle.body"))) {
			return;
		}
		try {
			EventService.SettlementReport report = events.settle(view.eventId(), option.optionId());
			statusLabel.setText(i18n.get("events.settled", report.winnerCount(),
				report.totalPaid().toPlainString()));
			announcer.refresh(view.eventId());
			refresh();
		} catch (BetValidationException e) {
			statusLabel.setText(i18n.get(e.messageKey(), e.arguments()));
		}
	}

	private void actOnSelected(java.util.function.LongConsumer action, String successKey) {
		EventQueryService.EventView view = eventTable.getSelectionModel().getSelectedItem();
		if (view == null) {
			return;
		}
		try {
			action.accept(view.eventId());
			statusLabel.setText(i18n.get(successKey, "#" + view.eventId()));
			announcer.refresh(view.eventId());
			refresh();
		} catch (BetValidationException e) {
			statusLabel.setText(i18n.get(e.messageKey(), e.arguments()));
		}
	}

	private EventEditorController newEditor() {
		return (EventEditorController) viewLoader.loadWithController("/fxml/dialogs/event-editor.fxml")
			.controller();
	}

	private Window window() {
		return eventTable.getScene() == null ? null : eventTable.getScene().getWindow();
	}

	/** Currency symbol of the selected guild, used by the editor preview. */
	String symbolOf(long guildId) {
		return guildConfig.currencySymbol(guildId);
	}

	/** Formats an amount for the detail panel. */
	String format(java.math.BigDecimal amount) {
		return amount.setScale(Money.SCALE, java.math.RoundingMode.HALF_UP).toPlainString();
	}
}
