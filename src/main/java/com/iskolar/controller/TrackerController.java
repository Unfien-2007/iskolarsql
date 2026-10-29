package com.iskolar.controller;

import com.iskolar.dao.DaoFactory;
import com.iskolar.dto.ApplicationCard;
import com.iskolar.dto.MatchResult;
import com.iskolar.model.ApplicationStatus;
import com.iskolar.service.ServiceFactory;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ViewNavigator;
import com.iskolar.view.JourneyRail;
import com.iskolar.view.ScholarshipDetailsDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kanban board: one column per {@link ApplicationStatus}, cards move via
 * forward-transition buttons. Guests and profile-less students see a gate.
 */
public class TrackerController {

    @FXML
    private VBox boardPane;
    @FXML
    private HBox boardRow;
    @FXML
    private VBox gatePane;
    @FXML
    private Label gateLabel;
    @FXML
    private Button gateButton;

    private int studentId = -1;

    @FXML
    private void initialize() {
        var session = SessionManager.current();
        if (session.isEmpty()) {
            showGate("Log in to track your scholarship applications.", "Log in",
                    () -> ViewNavigator.show(ViewNavigator.LOGIN_VIEW));
            return;
        }
        var profile = DaoFactory.studentProfileDao().findByUserId(session.get().userId());
        if (profile.isEmpty()) {
            showGate("Complete your profile wizard first — your tracker is tied to your student profile.",
                    "Open wizard", () -> ViewNavigator.show(ViewNavigator.WIZARD_VIEW));
            return;
        }
        studentId = profile.get().studentId();
        gatePane.setVisible(false);
        gatePane.setManaged(false);
        refresh();
    }

    private void showGate(String message, String buttonText, Runnable action) {
        boardPane.setVisible(false);
        boardPane.setManaged(false);
        gateLabel.setText(message);
        gateButton.setText(buttonText);
        gateButton.setOnAction(e -> action.run());
    }

    private void refresh() {
        List<ApplicationCard> cards = ServiceFactory.applicationService().cardsForStudent(studentId);
        Map<Long, MatchResult> matches = ServiceFactory.matchingService().matchAll(studentId).stream()
                .collect(Collectors.toMap(MatchResult::scholarshipId, r -> r));

        boardRow.getChildren().clear();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            boardRow.getChildren().add(buildColumn(status, cards, matches));
        }
        JourneyRail.setAwarded(cards.stream()
                .anyMatch(c -> c.application().status() == ApplicationStatus.AWARDED));
    }

    private VBox buildColumn(ApplicationStatus status, List<ApplicationCard> cards,
                             Map<Long, MatchResult> matches) {
        VBox column = new VBox(8);
        column.getStyleClass().add("kanban-column");

        Label title = new Label(status.displayName());
        title.getStyleClass().add("kanban-title");
        Label count = new Label(String.valueOf(cards.stream()
                .filter(c -> c.application().status() == status).count()));
        count.getStyleClass().add("kanban-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, count);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        column.getChildren().add(header);

        VBox cardPane = new VBox(8);
        VBox.setVgrow(cardPane, Priority.ALWAYS);
        column.getChildren().add(cardPane);

        List<ApplicationCard> columnCards = cards.stream()
                .filter(c -> c.application().status() == status)
                .toList();
        if (columnCards.isEmpty()) {
            Label empty = new Label("No applications");
            empty.getStyleClass().add("kanban-empty");
            cardPane.getChildren().add(empty);
        } else {
            for (ApplicationCard card : columnCards) {
                cardPane.getChildren().add(TrackerCardController.load(
                        card,
                        matches.get(card.scholarship().scholarshipId()),
                        next -> {
                            ServiceFactory.applicationService().move(card.application().applicationId(), next);
                            refresh();
                        },
                        () -> {
                            ServiceFactory.applicationService().remove(card.application().applicationId());
                            refresh();
                        },
                        () -> openDetails(card.scholarship().scholarshipId(), matches)));
            }
        }
        return column;
    }

    /** Opens the full scholarship details modal (fired by clicking a card). */
    private void openDetails(long scholarshipId, Map<Long, MatchResult> matches) {
        var bundle = DaoFactory.scholarshipDao().findEligibilityBundle(scholarshipId).orElse(null);
        if (bundle == null) {
            return;
        }
        ScholarshipDetailsDialog.show(boardRow.getScene().getWindow(), bundle, matches.get(scholarshipId));
    }
}
