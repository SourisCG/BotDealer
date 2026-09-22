/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.ui.sections;

import java.math.BigDecimal;
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
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import souris.botdealer.domain.LedgerEntry;
import souris.botdealer.domain.Wallet;
import souris.botdealer.i18n.I18nService;
import souris.botdealer.service.discord.UiEventBus;
import souris.botdealer.service.discord.UiEvents;
import souris.botdealer.service.economy.InsufficientFundsException;
import souris.botdealer.service.economy.LedgerService;
import souris.botdealer.service.economy.WalletService;
import souris.botdealer.settings.AppSettingKey;
import souris.botdealer.settings.AppSettingsService;
import souris.botdealer.ui.UiSelectionModel;

/**
 * Wallets screen: balances for the selected guild, an admin adjustment action and the
 * full ledger of the selected member.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WalletsController {

	private static final DateTimeFormatter WHEN_FORMAT =
		DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

	@FXML
	private Label titleLabel;
	@FXML
	private ComboBox<UiSelectionModel.GuildChoice> guildCombo;
	@FXML
	private TextField searchField;
	@FXML
	private Button grantButton;
	@FXML
	private Button removeButton;
	@FXML
	private Button setButton;
	@FXML
	private Label summaryLabel;
	@FXML
	private TableView<Wallet> walletTable;
	@FXML
	private TableColumn<Wallet, String> memberColumn;
	@FXML
	private TableColumn<Wallet, String> balanceColumn;
	@FXML
	private TableColumn<Wallet, String> dailyColumn;
	@FXML
	private Label historyTitle;
	@FXML
	private TableView<LedgerEntry> ledgerTable;
	@FXML
	private TableColumn<LedgerEntry, String> ledgerWhenColumn;
	@FXML
	private TableColumn<LedgerEntry, String> ledgerReasonColumn;
	@FXML
	private TableColumn<LedgerEntry, String> ledgerDeltaColumn;
	@FXML
	private TableColumn<LedgerEntry, String> ledgerAfterColumn;
	@FXML
	private Label statusLabel;

	private final I18nService i18n;
	private final WalletService wallets;
	private final LedgerService ledger;
	private final AppSettingsService settings;
	private final UiSelectionModel selection;

	public WalletsController(I18nService i18n, WalletService wallets, LedgerService ledger,
			AppSettingsService settings, UiSelectionModel selection, UiEventBus uiEvents) {
		this.i18n = i18n;
		this.wallets = wallets;
		this.ledger = ledger;
		this.settings = settings;
		this.selection = selection;
		uiEvents.subscribe(event -> {
			if (event instanceof UiEvents.EconomyChanged) {
				refresh();
			}
		});
	}

	public void initialize() {
		titleLabel.setText(i18n.get("sections.wallets.title"));
		grantButton.setText(i18n.get("wallets.action.grant"));
		removeButton.setText(i18n.get("wallets.action.remove"));
		setButton.setText(i18n.get("wallets.action.set"));
		historyTitle.setText(i18n.get("wallets.history"));

		memberColumn.setCellValueFactory(cell -> new SimpleStringProperty(
			cell.getValue().getDisplayName() == null ? ("#" + cell.getValue().getUserId())
				: cell.getValue().getDisplayName()));
		balanceColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(cell.getValue().getBalance().toPlainString()));
		dailyColumn.setCellValueFactory(cell -> new SimpleStringProperty(
			cell.getValue().getLastDailyAt() == null ? i18n.get("wallets.never")
				: WHEN_FORMAT.format(cell.getValue().getLastDailyAt())));

		ledgerWhenColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(WHEN_FORMAT.format(cell.getValue().getCreatedAt())));
		ledgerReasonColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(i18n.get(cell.getValue().getReason().labelKey())));
		ledgerDeltaColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(cell.getValue().getDelta().toPlainString()));
		ledgerAfterColumn.setCellValueFactory(cell ->
			new SimpleStringProperty(cell.getValue().getBalanceAfter().toPlainString()));

		guildCombo.setItems(selection.guilds());
		guildCombo.valueProperty().bindBidirectional(selection.selectedProperty());
		guildCombo.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
		searchField.textProperty().addListener((observable, oldValue, newValue) -> refresh());
		walletTable.getSelectionModel().selectedItemProperty()
			.addListener((observable, oldValue, newValue) -> showHistory(newValue));
		refresh();
	}

	@FXML
	private void refresh() {
		selection.selectedGuildId().ifPresentOrElse(guildId -> {
			walletTable.setItems(FXCollections.observableArrayList(wallets.search(guildId, searchField.getText())));
			String symbol = settings.get(AppSettingKey.CURRENCY_SYMBOL);
			summaryLabel.setText(i18n.get("wallets.summary", wallets.count(guildId),
				symbol + " " + wallets.totalInCirculation(guildId).toPlainString()));
			if (!walletTable.getItems().isEmpty() && walletTable.getSelectionModel().getSelectedItem() == null) {
				walletTable.getSelectionModel().selectFirst();
			}
		}, () -> {
			walletTable.setItems(FXCollections.observableArrayList());
			summaryLabel.setText(i18n.get("dashboard.noGuild"));
		});
	}

	private void showHistory(Wallet wallet) {
		if (wallet == null) {
			ledgerTable.setItems(FXCollections.observableArrayList());
			return;
		}
		historyTitle.setText(i18n.get("wallets.history.of",
			wallet.getDisplayName() == null ? "#" + wallet.getUserId() : wallet.getDisplayName()));
		ledgerTable.setItems(FXCollections.observableArrayList(
			ledger.history(wallet.getGuildId(), wallet.getUserId(), 200)));
	}

	// ------------------------------------------------------------------ actions

	@FXML
	private void grant() {
		adjust(true);
	}

	@FXML
	private void remove() {
		adjust(false);
	}

	private void adjust(boolean grant) {
		Wallet wallet = walletTable.getSelectionModel().getSelectedItem();
		if (wallet == null) {
			statusLabel.setText(i18n.get("wallets.error.selectMember"));
			return;
		}
		Optional<String> input = askAmount(i18n.get(grant ? "wallets.action.grant" : "wallets.action.remove"));
		input.ifPresent(raw -> {
			try {
				BigDecimal amount = new BigDecimal(raw.replace(',', '.'));
				BigDecimal delta = grant ? amount : amount.negate();
				Wallet updated = wallets.adjust(wallet.getGuildId(), wallet.getUserId(),
					wallet.getDisplayName(), delta, 0L);
				statusLabel.setText(i18n.get("wallets.updated", updated.getBalance().toPlainString()));
				refresh();
				showHistory(updated);
			} catch (NumberFormatException e) {
				statusLabel.setText(i18n.get("discord.error.invalidAmount"));
			} catch (InsufficientFundsException e) {
				statusLabel.setText(i18n.get("bet.error.insufficientFunds", e.balance(), e.requested()));
			}
		});
	}

	@FXML
	private void setBalance() {
		Wallet wallet = walletTable.getSelectionModel().getSelectedItem();
		if (wallet == null) {
			statusLabel.setText(i18n.get("wallets.error.selectMember"));
			return;
		}
		askAmount(i18n.get("wallets.action.set")).ifPresent(raw -> {
			try {
				BigDecimal target = new BigDecimal(raw.replace(',', '.'));
				BigDecimal delta = target.subtract(wallet.getBalance());
				Wallet updated = wallets.adjust(wallet.getGuildId(), wallet.getUserId(),
					wallet.getDisplayName(), delta, 0L);
				statusLabel.setText(i18n.get("wallets.updated", updated.getBalance().toPlainString()));
				refresh();
			} catch (NumberFormatException e) {
				statusLabel.setText(i18n.get("discord.error.invalidAmount"));
			} catch (InsufficientFundsException e) {
				statusLabel.setText(i18n.get("bet.error.insufficientFunds", e.balance(), e.requested()));
			}
		});
	}

	private Optional<String> askAmount(String title) {
		TextInputDialog dialog = new TextInputDialog();
		dialog.setTitle(title);
		dialog.setHeaderText(title);
		dialog.setContentText(i18n.get("wallets.amountPrompt"));
		if (walletTable.getScene() != null) {
			dialog.initOwner(walletTable.getScene().getWindow());
		}
		return dialog.showAndWait().map(String::strip).filter(value -> !value.isEmpty());
	}
}
