package com.iskolar.controller;

import com.iskolar.dto.MatchResult;
import com.iskolar.model.DeadlineUrgency;
import com.iskolar.model.MatchTier;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.iskolar.util.Dates;
import com.iskolar.view.Motion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Discovery-grid card: provider pill with a type dot, urgency pill, title,
 * provider, closing date, a two-column tuition/stipend block, and a
 * Save / compare action row.
 *
 * <p>The whole card opens the details drawer (mouse or Enter/Space); the Save
 * button and compare toggle consume their own clicks.</p>
 */
public class ScholarshipCardController {

    @FXML
    private HBox providerBadge;
    @FXML
    private Region providerDot;
    @FXML
    private Label providerBadgeText;
    @FXML
    private Label urgencyPill;
    @FXML
    private Label titleLabel;
    @FXML
    private Label providerLabel;
    @FXML
    private Label closesLabel;
    @FXML
    private Label tuitionValue;
    @FXML
    private Label stipendValue;
    @FXML
    private Label matchBadge;
    @FXML
    private Button saveButton;
    @FXML
    private ToggleButton compareToggle;

    private BorderPane root;
    private Scholarship scholarship;
    private Predicate<Scholarship> onBookmark;
    private BiConsumer<Scholarship, Boolean> onCompareToggle;
    private Consumer<Scholarship> onDetails;
    /** Guards {@link #setCompareSelected} so restoring state never re-fires the toggle. */
    private boolean suppressCompareEvents;
    private boolean saved;

    /**
     * Loads one card.
     *
     * @param saved           {@code true} when the scholarship is already on the student's tracker
     * @param onBookmark      called when Save is pressed; {@code true} = persisted
     * @param onCompareToggle called with (scholarship, selected) when the compare toggle flips
     * @param onDetails       called when the card itself is clicked
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
                return; // let inner buttons/toggle handle their own keys
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
        compareToggle.setSelected(selected);
        suppressCompareEvents = false;
    }

    /** Locks the compare toggle when the 3-scholarship limit is reached. */
    public void setCompareLocked(boolean locked) {
        compareToggle.setDisable(locked);
    }

    public void setScholarship(Scholarship s) {
        this.scholarship = s;
        titleLabel.setText(s.title());
        providerLabel.setText(s.providerName());
        providerBadgeText.setText(s.providerType().displayName());
        providerBadge.getStyleClass().add(providerStyle(s.providerType()));
        closesLabel.setText(closesText(s.deadlineDate()));
        tuitionValue.setText(s.tuitionBenefit() == null || s.tuitionBenefit().isBlank()
                ? "See provider" : s.tuitionBenefit());
        stipendValue.setText(s.stipendLabel());
        urgencyPill.setText("•  " + DeadlineUrgency.text(s.deadlineDate()));
        urgencyPill.getStyleClass().add(urgencyStyle(s.deadlineDate()));
        if ("pill-red".equals(urgencyStyle(s.deadlineDate()))) {
            Motion.pulse(urgencyPill);
        }

        compareToggle.setGraphic(FontIcon.of(Feather.COLUMNS, 15));
        compareToggle.setTooltip(new Tooltip("Add to comparison"));
        compareToggle.setAccessibleText("Compare " + s.title());
        compareToggle.selectedProperty().addListener((obs, old, checked) -> {
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

    /** Flips the Save button into its filled, already-tracked state. */
    private void setSaved(boolean isSaved) {
        saved = isSaved;
        saveButton.setText(isSaved ? "Saved" : "Save");
        saveButton.setGraphic(FontIcon.of(isSaved ? Feather.CHECK : Feather.BOOKMARK, 14));
        saveButton.getStyleClass().remove("saved");
        if (isSaved) {
            saveButton.getStyleClass().add("saved");
        }
        saveButton.setDisable(isSaved);
        saveButton.setAccessibleText(isSaved
                ? "Already saved to your tracker"
                : "Save " + (scholarship == null ? "scholarship" : scholarship.title()));
    }

    /** {@code null} = guest — badge hidden; the header prompt covers it. */
    public void setMatch(MatchResult match) {
        if (match == null) {
            matchBadge.setVisible(false);
            matchBadge.setManaged(false);
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
        matchBadge.setAccessibleText("Match score " + match.scoreLabel());
    }

    /** "Closes Nov 5, 2026" / "Closed Nov 4, 2026" / "No deadline". */
    private static String closesText(LocalDate deadline) {
        if (deadline == null) {
            return "No deadline";
        }
        String date = Dates.displayDate(deadline);
        return deadline.isBefore(LocalDate.now()) ? "Closed " + date : "Closes " + date;
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
