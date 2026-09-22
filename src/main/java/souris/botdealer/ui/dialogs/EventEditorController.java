/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.dialogs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.domain.PayoutMode;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.betting.BetValidationException;
import souris.botdealer.service.betting.EventService;

/**
 * Event editor dialog: title, description, payout mode, dynamic option rows, closing
 * time and rake, with a live fixed-odds liability preview.
 *
 * <p>Validation runs against {@link EventService} rules before the dialog closes, so the
 * user sees the same message the service would raise instead of a dialog that vanishes
 * and a failure afterwards.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EventEditorController {

	@FXML
	private VBox root;
	@FXML
	private Label headingLabel;
	@FXML
	private Label titleFieldLabel;
	@FXML
	private TextField titleField;
	@FXML
	private Label descriptionFieldLabel;
	@FXML
	private TextArea descriptionField;
	@FXML
	private Label modeLabel;
	@FXML
	private RadioButton parimutuelButton;
	@FXML
	private RadioButton fixedOddsButton;
	@FXML
	private Label modeHintLabel;
	@FXML
	private Label optionsLabel;
	@FXML
	private Button addOptionButton;
	@FXML
	private VBox optionBox;
	@FXML
	private Label liabilityLabel;
	@FXML
	private Label closesInLabel;
	@FXML
	private Spinner<Integer> closesInSpinner;
	@FXML
	private Label rakeLabel;
	@FXML
	private Spinner<Integer> rakeSpinner;
	@FXML
	private Label errorLabel;

	private final I18nService i18n;
	private final EventService events;
	private final ToggleGroup modeGroup = new ToggleGroup();
	private final List<OptionRow> optionRows = new ArrayList<>();

	private Dialog<EventService.CreateRequest> dialog;
	private long guildId;

	public EventEditorController(I18nService i18n, EventService events) {
		this.i18n = i18n;
		this.events = events;
	}

	/** Opens the dialog and returns the request when the user confirms. */
	public Optional<EventService.CreateRequest> show(Window owner, long guildId) {
		this.guildId = guildId;
		buildDialog(owner);
		return Optional.ofNullable(dialog.showAndWait().orElse(null));
	}

	// ------------------------------------------------------------------ dialog

	private void buildDialog(Window owner) {
		dialog = new Dialog<>();
		dialog.setTitle(i18n.get("events.editor.title"));
		dialog.initModality(Modality.WINDOW_MODAL);
		if (owner != null) {
			dialog.initOwner(owner);
		}
		dialog.getDialogPane().setContent(root);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		headingLabel.setText(i18n.get("events.editor.heading"));
		titleFieldLabel.setText(i18n.get("events.editor.titleLabel"));
		descriptionFieldLabel.setText(i18n.get("events.editor.descriptionLabel"));
		modeLabel.setText(i18n.get("events.editor.modeLabel"));
		optionsLabel.setText(i18n.get("events.editor.optionsLabel"));
		addOptionButton.setText(i18n.get("events.editor.addOption"));
		closesInLabel.setText(i18n.get("events.editor.closesIn"));
		rakeLabel.setText(i18n.get("events.editor.rake"));

		parimutuelButton.setToggleGroup(modeGroup);
		fixedOddsButton.setToggleGroup(modeGroup);
		parimutuelButton.setText(i18n.get("event.mode.parimutuel"));
		fixedOddsButton.setText(i18n.get("event.mode.fixedOdds"));
		parimutuelButton.setSelected(true);
		modeGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
			boolean fixed = fixedOddsButton.isSelected();
			modeHintLabel.setText(i18n.get(fixed ? "event.mode.fixedOdds.hint" : "event.mode.parimutuel.hint"));
			optionRows.forEach(row -> row.setOddsEnabled(fixed));
			updateLiability();
		});
		modeHintLabel.setText(i18n.get("event.mode.parimutuel.hint"));

		closesInSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20160, 1440));
		rakeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 0));

		addOption();
		addOption();
		updateLiability();

		Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.setText(i18n.get("events.editor.create"));
		okButton.addEventFilter(ActionEvent.ACTION, event -> {
			String error = validate();
			if (error != null) {
				errorLabel.setText(error);
				event.consume();
			}
		});
	}

	// ------------------------------------------------------------------ options

	@FXML
	private void addOption() {
		if (optionRows.size() >= 10) {
			errorLabel.setText(i18n.get("events.editor.tooManyOptions"));
			return;
		}
		OptionRow row = new OptionRow();
		optionRows.add(row);
		optionBox.getChildren().add(row.node());
		row.setOddsEnabled(fixedOddsButton.isSelected());
		updateLiability();
	}

	private void removeOption(OptionRow row) {
		if (optionRows.size() <= 2) {
			errorLabel.setText(i18n.get("events.editor.minOptions"));
			return;
		}
		optionRows.remove(row);
		optionBox.getChildren().remove(row.node());
		updateLiability();
	}

	/** Recomputes the worst-case payout for fixed-odds events. */
	private void updateLiability() {
		if (!fixedOddsButton.isSelected()) {
			liabilityLabel.setText("");
			return;
		}
		liabilityLabel.setText(i18n.get("events.editor.liabilityHint"));
	}

	// ------------------------------------------------------------------ validation

	private String validate() {
		try {
			buildRequest();
			return null;
		} catch (BetValidationException e) {
			return i18n.get(e.messageKey(), e.arguments());
		} catch (NumberFormatException e) {
			return i18n.get("discord.error.invalidAmount");
		}
	}

	private EventService.CreateRequest buildRequest() {
		PayoutMode mode = fixedOddsButton.isSelected() ? PayoutMode.FIXED_ODDS : PayoutMode.PARIMUTUEL;
		List<EventService.OptionDraft> drafts = new ArrayList<>();
		for (OptionRow row : optionRows) {
			String label = row.label();
			if (label.isBlank()) {
				continue;
			}
			drafts.add(new EventService.OptionDraft(label, mode == PayoutMode.FIXED_ODDS ? row.odds() : null));
		}

		Integer closesInMinutes = closesInSpinner.getValue();
		Instant closesAt = closesInMinutes == null || closesInMinutes <= 0
			? null
			: Instant.now().plus(Duration.ofMinutes(closesInMinutes));
		Integer rake = rakeSpinner.getValue();

		// Reuse the service's own rules so the dialog and the bot agree on what is valid.
		return new EventService.CreateRequest(guildId, 0L, titleField.getText(),
			descriptionField.getText(), mode, closesAt,
			rake == null ? BigDecimal.ZERO : BigDecimal.valueOf(rake), null, drafts, true);
	}

	/** One editable option row: label, optional odds and a remove button. */
	private final class OptionRow {

		private final TextField label = new TextField();
		private final TextField odds = new TextField();
		private final Button remove = new Button("✕");
		private final HBox node;

		private OptionRow() {
			label.setPromptText(i18n.get("events.editor.optionPrompt"));
			odds.setPromptText(i18n.get("events.editor.oddsPrompt"));
			odds.setPrefWidth(80);
			remove.getStyleClass().add("small-button");
			remove.setOnAction(event -> removeOption(this));
			node = new HBox(8, label, odds, remove);
			HBox.setHgrow(label, javafx.scene.layout.Priority.ALWAYS);
		}

		private HBox node() {
			return node;
		}

		private String label() {
			return label.getText() == null ? "" : label.getText().strip();
		}

		private BigDecimal odds() {
			String value = odds.getText() == null ? "" : odds.getText().strip().replace(',', '.');
			if (value.isEmpty()) {
				throw new BetValidationException("event.error.oddsTooLow", label());
			}
			BigDecimal parsed = new BigDecimal(value);
			return parsed.setScale(2, RoundingMode.HALF_UP);
		}

		private void setOddsEnabled(boolean enabled) {
			odds.setDisable(!enabled);
		}
	}
}
