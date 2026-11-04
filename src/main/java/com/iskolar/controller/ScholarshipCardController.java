package com.iskolar.controller;

import com.iskolar.dto.MatchResult;
import com.iskolar.model.DeadlineUrgency;
import com.iskolar.model.MatchTier;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.iskolar.util.Dates;
import com.iskolar.view.Motion;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Discovery-grid card component: provider badge, deadline urgency pill,
 * financial highlights, match badge, a Save (bookmark) action and a Compare
 * checkbox feeding the side-by-side modal.
 */
public class ScholarshipCardController {

    /**
     * Rough width of the card footer consumed by the Save button, the Compare
     * checkbox, the match badge and their 10px gaps. The tier caption is capped
     * to whatever is left so it ellipsizes instead of pushing the badge off the card.
     */
    private static final double FOOTER_RESERVED_WIDTH = 220;

    @FXML
    private Label providerBadge;
    @FXML
    private Label urgencyPill;
    @FXML
    private Label titleLabel;
    @FXML
    private Label providerLabel;
    @FXML
    private Label deadlineLabel;
    @FXML
    private Label tuitionLabel;
    @FXML
    private Label stipendLabel;
    @FXML
    private Label matchBadge;
    @FXML
    private Label matchCaption;
    @FXML
    private Button saveButton;
    @FXML
    private CheckBox compareCheck;

    private BorderPane root;
    private Scholarship scholarship;
    private Predicate<Scholarship> onBookmark;
    private BiConsumer<Scholarship, Boolean> onCompareToggle;
    private Consumer<Scholarship> onDetails;
    /** Guards {@link #setCompareSelected} so restoring state never re-fires the toggle. */
    private boolean suppressCompareEvents;

    /**
     * Loads one card.
     *
     * @param saved           {@code true} when the scholarship is already on the student's tracker
     * @param onBookmark      called when the Save button is pressed; {@code true} = persisted
     * @param onCompareToggle called with (scholarship, checked) when the checkbox toggles
     * @param onDetails       called when the card itself is clicked (buttons/checkbox consume their own clicks)
     */
    public static ScholarshipCardController load(Scholarship scholarship, MatchResult match,
                                                 boolean saved,
                                                 Predicate<Scholarship> onBookmark,
                                                 BiConsumer<Scholarship, Boolean> onCompareToggle,
                                                 Consumer<Scholarship> onDetails) {
        FXMLLoader loader = new FXMLLoader(
                ScholarshipCardController.class.getResource("/view/scholarship_card.fxml"));
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load scholarship card", e);
        }
        ScholarshipCardController controller = loader.getController();
        controller.root = loader.getRoot();
        controller.onBookmark = onBookmark;
        controller.onCompareToggle = onCompareToggle;
        controller.onDetails = onDetails;
        controller.setScholarship(scholarship);
        controller.setMatch(match);
        controller.setSaved(saved);
        // Keeps the tier caption from shoving the match badge off a narrow card.
        controller.matchCaption.maxWidthProperty().bind(
                Bindings.max(40, controller.root.widthProperty().subtract(FOOTER_RESERVED_WIDTH)));
        controller.root.setOnMouseClicked(e -> {
            if (controller.onDetails != null) {
                controller.onDetails.accept(scholarship);
            }
        });
        controller.root.setOnMouseEntered(e -> Motion.hoverLift(controller.root, true));
        controller.root.setOnMouseExited(e -> Motion.hoverLift(controller.root, false));
        // The whole card is clickable, so it must also be reachable and
        // activatable from the keyboard (Tab to the card, Enter/Space to open).
        controller.root.setFocusTraversable(true);
        controller.root.setAccessibleRole(AccessibleRole.BUTTON);
        controller.root.setAccessibleText("Scholarship: " + scholarship.title()
                + ", offered by " + scholarship.providerName());
        controller.root.setOnKeyPressed(e -> {
            if (e.getTarget() != controller.root) {
                return; // let inner buttons/checkboxes handle their own keys
            }
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                if (controller.onDetails != null) {
                    controller.onDetails.accept(scholarship);
                }
                e.consume();
            }
        });
        return controller;
    }

    public Node getRoot() {
        return root;
    }

    /** Restores a persisted compare tick without replaying the toggle callback. */
    public void setCompareSelected(boolean selected) {
        suppressCompareEvents = true;
        compareCheck.setSelected(selected);
        suppressCompareEvents = false;
    }

    /** Locks the compare checkbox when the 3-scholarship limit is reached. */
    public void setCompareLocked(boolean locked) {
        compareCheck.setDisable(locked);
    }

    public void setScholarship(Scholarship s) {
        this.scholarship = s;
        titleLabel.setText(s.title());
        providerLabel.setText(s.providerName());
        providerBadge.setText(s.providerType().displayName());
        providerBadge.getStyleClass().add(providerStyle(s.providerType()));
        tuitionLabel.setText("Tuition: " + (s.tuitionBenefit() == null || s.tuitionBenefit().isBlank()
                ? "See provider" : s.tuitionBenefit()));
        stipendLabel.setText("Stipend: " + s.stipendLabel());
        // The urgency pill counts down; this line pins the actual date so a card
        // is actionable without opening the details drawer.
        deadlineLabel.setText(deadlineText(s.deadlineDate()));
        urgencyPill.setText(urgencyText(s.deadlineDate()));
        urgencyPill.getStyleClass().add(urgencyStyle(s.deadlineDate()));
        if ("pill-red".equals(urgencyStyle(s.deadlineDate()))) {
            Motion.pulse(urgencyPill);
        }

        compareCheck.selectedProperty().addListener((obs, old, checked) -> {
            if (!suppressCompareEvents) {
                onCompareToggle.accept(s, checked);
            }
        });
        saveButton.setOnAction(e -> {
            // Only flip the button when the bookmark actually landed (guests bounce to login).
            if (onBookmark.test(s)) {
                setSaved(true);
            }
        });
    }

    /** Flips the Save button into its disabled, already-tracked state. */
    private void setSaved(boolean saved) {
        saveButton.setText(saved ? "Saved ✓" : "Save");
        saveButton.setDisable(saved);
    }

    /** {@code null} = guest — badge hidden, caption prompts sign-in. */
    public void setMatch(MatchResult match) {
        if (match == null) {
            matchBadge.setVisible(false);
            matchBadge.setManaged(false);
            matchCaption.setText("Sign in for your match score");
            return;
        }
        matchBadge.getStyleClass().add(tierStyle(match.tier()));
        Motion.countUp(matchBadge, match.score(), match.scoreLabel());
        String tooltip;
        if (!match.eligible()) {
            tooltip = match.gateReason();
        } else if (match.warnings().isEmpty()) {
            tooltip = match.tier().label();
        } else {
            tooltip = match.tier().label() + " — " + String.join(" ", match.warnings());
        }
        Tooltip.install(matchBadge, new Tooltip(tooltip));
        matchCaption.setText(match.tier().label() + (match.borderlineGrade() ? " · Borderline GWA" : ""));
    }

    /** Absolute deadline date shown under the provider name. */
    private static String deadlineText(LocalDate deadline) {
        if (deadline == null) {
            return "Deadline: not specified";
        }
        String date = Dates.displayDate(deadline);
        return deadline.isBefore(LocalDate.now())
                ? "Closed " + date
                : "Deadline: " + date;
    }

    private static String urgencyText(LocalDate deadline) {
        if (deadline == null) {
            return "No deadline";
        }
        DeadlineUrgency urgency = DeadlineUrgency.of(deadline);
        long days = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        return switch (urgency) {
            case EXPIRED -> "Deadline passed";
            case RED -> "48h left";
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

    private static String providerStyle(ProviderType type) {
        return switch (type) {
            case GOVERNMENT, GOVERNMENT_OWNED_CORP -> "badge-gov";
            case PRIVATE_CORPORATE -> "badge-corp";
            case NGO_FOUNDATION -> "badge-ngo";
            case UNIVERSITY -> "badge-uni";
            case LOCAL_GOVERNMENT -> "badge-lgu";
            case INTERNATIONAL -> "badge-intl";
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
