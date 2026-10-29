package com.iskolar.controller;

import com.iskolar.dto.ApplicationCard;
import com.iskolar.dto.MatchResult;
import com.iskolar.model.ApplicationStatus;
import com.iskolar.model.DeadlineUrgency;
import com.iskolar.model.MatchTier;
import com.iskolar.view.Motion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

/**
 * Kanban card component: scholarship summary, deadline pill, match badge and
 * the forward-transition buttons for its current status.
 */
public class TrackerCardController {

    @FXML
    private Label titleLabel;
    @FXML
    private Label providerLabel;
    @FXML
    private Label deadlinePill;
    @FXML
    private Label matchBadge;
    @FXML
    private Label statusCaption;
    @FXML
    private Button removeButton;
    @FXML
    private HBox actionsBox;

    /**
     * Loads one Kanban card.
     *
     * @param onAdvance called with the target status when a forward button is pressed
     * @param onRemove  called when the card is removed
     * @param onDetails called when the card itself is clicked (buttons consume their own clicks)
     */
    public static Node load(ApplicationCard card, MatchResult match,
                            Consumer<ApplicationStatus> onAdvance, Runnable onRemove,
                            Runnable onDetails) {
        FXMLLoader loader = new FXMLLoader(TrackerCardController.class.getResource("/view/tracker_card.fxml"));
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load tracker card", e);
        }
        TrackerCardController controller = loader.getController();
        controller.init(card, match, onAdvance, onRemove);
        Node root = loader.getRoot();
        root.setOnMouseClicked(e -> {
            if (onDetails != null) {
                onDetails.run();
            }
        });
        root.setOnMouseEntered(e -> Motion.hoverLift(root, true));
        root.setOnMouseExited(e -> Motion.hoverLift(root, false));
        return root;
    }

    private void init(ApplicationCard card, MatchResult match,
                      Consumer<ApplicationStatus> onAdvance, Runnable onRemove) {
        titleLabel.setText(card.scholarship().title());
        providerLabel.setText(card.scholarship().providerName());

        LocalDate deadline = card.scholarship().deadlineDate();
        deadlinePill.setText(urgencyText(deadline));
        deadlinePill.getStyleClass().add(urgencyStyle(deadline));
        if ("pill-red".equals(urgencyStyle(deadline))) {
            Motion.pulse(deadlinePill);
        }

        if (match == null) {
            matchBadge.setText("—");
            matchBadge.getStyleClass().add("match-ineligible");
        } else {
            matchBadge.getStyleClass().add(tierStyle(match.tier()));
            Motion.countUp(matchBadge, match.score(), match.scoreLabel());
            String tip = match.eligible()
                    ? (match.warnings().isEmpty() ? match.tier().label()
                    : match.tier().label() + " — " + String.join(" ", match.warnings()))
                    : match.gateReason();
            Tooltip.install(matchBadge, new Tooltip(tip));
        }
        statusCaption.setText(card.application().status().displayName());

        removeButton.setGraphic(FontIcon.of(Feather.TRASH_2, 14));
        removeButton.setOnAction(e -> onRemove.run());

        actionsBox.getChildren().clear();
        for (ApplicationStatus next : card.application().status().nextTransitions()) {
            Button button = new Button("→ " + next.displayName());
            button.getStyleClass().addAll("advance-button", "ghost-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(e -> onAdvance.accept(next));
            actionsBox.getChildren().add(button);
        }
    }

    private static String urgencyText(LocalDate deadline) {
        if (deadline == null) {
            return "No deadline";
        }
        DeadlineUrgency urgency = DeadlineUrgency.of(deadline);
        long days = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        return switch (urgency) {
            case EXPIRED -> "Deadline passed";
            case RED -> "Closes within 48h";
            default -> days + (days == 1 ? " day left" : " days left");
        };
    }

    private static String urgencyStyle(LocalDate deadline) {
        return switch (DeadlineUrgency.of(deadline)) {
            case GREEN -> "pill-green";
            case AMBER -> "pill-amber";
            case RED -> "pill-red";
            case EXPIRED -> "pill-expired";
        };
    }

    private static String tierStyle(MatchTier tier) {
        return switch (tier) {
            case PRIME -> "match-prime";
            case STRONG -> "match-strong";
            case STRETCH -> "match-stretch";
            case INELIGIBLE -> "match-ineligible";
        };
    }
}
