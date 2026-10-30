package com.iskolar.controller;

import com.iskolar.dao.DaoFactory;
import com.iskolar.dto.ScholarshipDraft;
import com.iskolar.model.Scholarship;
import com.iskolar.service.ServiceFactory;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ViewNavigator;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/**
 * Admin scholarship catalog: TableView with create / edit / deactivate /
 * delete. Non-admins (or guests) see an access gate.
 */
public class AdminDashboardController {

    @FXML
    private VBox contentBox;
    @FXML
    private VBox gatePane;
    @FXML
    private Label gateLabel;
    @FXML
    private Button gateButton;
    @FXML
    private Button newButton;
    @FXML
    private Button editButton;
    @FXML
    private Button toggleButton;
    @FXML
    private Button deleteButton;
    @FXML
    private TableView<Scholarship> table;
    @FXML
    private TableColumn<Scholarship, String> titleCol;
    @FXML
    private TableColumn<Scholarship, String> providerCol;
    @FXML
    private TableColumn<Scholarship, String> typeCol;
    @FXML
    private TableColumn<Scholarship, String> deadlineCol;
    @FXML
    private TableColumn<Scholarship, String> statusCol;

    @FXML
    private void initialize() {
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().title()));
        providerCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().providerName()));
        typeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().providerType().displayName()));
        deadlineCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().deadlineDate() == null ? "" : cd.getValue().deadlineDate().toString()));
        statusCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().isActive() ? "Active" : "Inactive"));
        statusCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label pill = new Label(item);
                    pill.getStyleClass().add(item.equals("Active") ? "status-active" : "status-inactive");
                    setGraphic(pill);
                }
            }
        });

        table.setRowFactory(tv -> {
            TableRow<Scholarship> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openEditor(row.getItem().scholarshipId());
                }
            });
            return row;
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> updateButtons());

        newButton.setOnAction(e -> ViewNavigator.show(ViewNavigator.ADMIN_FORM_VIEW,
                c -> ((AdminScholarshipFormController) c).initNew()));
        editButton.setOnAction(e -> openEditor(selected().scholarshipId()));
        toggleButton.setOnAction(e -> {
            Scholarship s = selected();
            try {
                var bundle = DaoFactory.scholarshipDao()
                        .findEligibilityBundle(s.scholarshipId()).orElse(null);
                if (bundle == null) {
                    load();
                    return;
                }
                Scholarship flipped = new Scholarship(
                        s.scholarshipId(), s.createdBy(), s.title(), s.providerName(),
                        s.providerType(), s.description(), s.tuitionBenefit(),
                        s.monthlyStipend(), s.returnServiceYears(), s.applicationUrl(),
                        s.openDate(), s.deadlineDate(), !s.isActive());
                ScholarshipDraft draft = new ScholarshipDraft(
                        flipped, bundle.criteria(), bundle.allowedProgramIds(),
                        bundle.allowedYearLevels(), bundle.tagRequirements(),
                        bundle.requiredDocumentIds());
                ServiceFactory.adminGateway().update(
                        contentBox.getScene().getWindow(), adminEmail(), s.scholarshipId(), draft);
                load();
            } catch (com.iskolar.sync.SyncException ex) {
                com.iskolar.sync.AdminCatalogGateway.showError(
                        contentBox.getScene().getWindow(), ex.getMessage());
                load();
            }
        });
        deleteButton.setOnAction(e -> {
            Scholarship s = selected();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete \"" + s.title() + "\"? This removes its criteria, rules, and applications.",
                    ButtonType.YES, ButtonType.CANCEL);
            confirm.setTitle("Delete scholarship");
            confirm.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> {
                try {
                    ServiceFactory.adminGateway().delete(
                            contentBox.getScene().getWindow(), adminEmail(), s.scholarshipId());
                    load();
                } catch (com.iskolar.sync.SyncException ex) {
                    com.iskolar.sync.AdminCatalogGateway.showError(
                            contentBox.getScene().getWindow(), ex.getMessage());
                    load();
                }
            });
        });

        if (!SessionManager.isAdmin()) {
            contentBox.setVisible(false);
            contentBox.setManaged(false);
            gateLabel.setText(SessionManager.current().isEmpty()
                    ? "This area is restricted to administrator accounts."
                    : "Your account does not have administrator access.");
            gateButton.setText(SessionManager.current().isEmpty() ? "Log in" : "Back to Discover");
            gateButton.setOnAction(e -> ViewNavigator.show(
                    SessionManager.current().isEmpty() ? ViewNavigator.LOGIN_VIEW : ViewNavigator.DISCOVERY_VIEW));
        } else {
            gatePane.setVisible(false);
            gatePane.setManaged(false);
            load();
        }
    }

    private void openEditor(long scholarshipId) {
        ViewNavigator.show(ViewNavigator.ADMIN_FORM_VIEW,
                c -> ((AdminScholarshipFormController) c).initEdit(scholarshipId));
    }

    private Scholarship selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private String adminEmail() {
        return SessionManager.current().map(SessionManager.Session::email).orElse("");
    }

    private void updateButtons() {
        boolean hasSelection = selected() != null;
        editButton.setDisable(!hasSelection);
        toggleButton.setDisable(!hasSelection);
        deleteButton.setDisable(!hasSelection);
        if (hasSelection) {
            toggleButton.setText(selected().isActive() ? "Deactivate" : "Activate");
        }
    }

    private void load() {
        table.getItems().setAll(DaoFactory.scholarshipDao().findAll());
        table.sort();
        updateButtons();
    }

    /** Deadline helper retained for future urgency display in the table. */
    @SuppressWarnings("unused")
    private String daysLeft(LocalDate deadline) {
        if (deadline == null) {
            return "";
        }
        return String.valueOf(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline));
    }
}
