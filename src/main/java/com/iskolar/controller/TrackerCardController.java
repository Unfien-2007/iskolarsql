package com.iskolar.controller;

import com.iskolar.dto.ApplicationCard;
import com.iskolar.dto.MatchResult;
import com.iskolar.model.ApplicationStatus;
import com.iskolar.model.DeadlineUrgency;
import com.iskolar.model.MatchTier;
import com.iskolar.view.Motion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
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
    private Button removeButton;
    @FXML
    private FlowPane actionsBox;

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
        // Cards fill whatever width the column has, so narrowing the board to
        // fit six stages never leaves a card wider than its column.
        if (root instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        root.setOnMouseClicked(e -> {
            if (onDetails != null) {
                onDetails.run();
            }
        });
        root.setOnMouseEntered(e -> Motion.hoverLift(root, true));
        root.setOnMouseExited(e -> Motion.hoverLift(root, false));
        root.setFocusTraversable(true);
        root.setAccessibleRole(AccessibleRole.BUTTON);
        root.setAccessibleText(card.scholarship().title() + ", "
                + card.application().status().displayName());
        root.setOnKeyPressed(e -> {
            if (e.getTarget() != root) {
                return;
            }
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                if (onDetails != null) {
                    onDetails.run();
                }
                e.consume();
            }
        });
        return root;
    }

    private void init(ApplicationCard card, MatchResult match,
                      Consumer<ApplicationStatus> onAdvance, Runnable onRemove) {
        titleLabel.setText(card.scholarship().title());
        providerLabel.setText(card.scholarship().providerName());

        LocalDate deadline = card.scholarship().deadlineDate();
        deadlinePill.setText("•  " + DeadlineUrgency.text(deadline));
        deadlinePill.getStyleClass().add(urgencyStyle(deadline));
        // The card footer shares one row with the match badge; without a min
        // width the pill shrinks and ellipsizes ("Closes within ...") first.
        deadlinePill.setMinWidth(Region.USE_PREF_SIZE);
        matchBadge.setMinWidth(Region.USE_PREF_SIZE);
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

        removeButton.setGraphic(FontIcon.of(Feather.TRASH_2, 12));
        removeButton.setAccessibleText("Remove " + card.scholarship().title() + " from tracker");
        removeButton.setOnAction(e -> onRemove.run());

        actionsBox.getChildren().clear();
        for (ApplicationStatus next : card.application().status().nextTransitions()) {
            // Short verbs keep two actions readable inside a ~168px column;
            // the full stage name is still in the tooltip.
            Button button = new Button(actionLabel(next));
            button.getStyleClass().addAll("advance-button", "ghost-button");
            button.setTooltip(new Tooltip("Move to " + next.displayName()));
            button.setOnAction(e -> onAdvance.accept(next));
            actionsBox.getChildren().add(button);
        }
    }

    /** Imperative button label for a target stage (see {@link ApplicationStatus#displayName()}). */
    private static String actionLabel(ApplicationStatus target) {
        return switch (target) {
            case SAVED -> "Save";
            case PREPARING_DOCS -> "Prepare docs";
            case APPLIED -> "Applied";
            case INTERVIEW -> "Interview";
            case AWARDED -> "Awarded";
            case REJECTED -> "Reject";
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
