package com.iskolar.controller;

import com.iskolar.dto.MatchResult;
import com.iskolar.model.DeadlineUrgency;
import com.iskolar.model.MatchTier;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Discovery-grid card component: provider badge, deadline urgency pill,
 * financial highlights, match badge, a Save (bookmark) action and a Compare
 * checkbox feeding the side-by-side modal.
 */
public class ScholarshipCardController {

    @FXML
    private Label providerBadge;
    @FXML
    private Label urgencyPill;
    @FXML
    private Label titleLabel;
    @FXML
    private Label providerLabel;
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

    private Parent root;
    private Scholarship scholarship;
    private Consumer<Scholarship> onBookmark;
    private BiConsumer<Scholarship, Boolean> onCompareToggle;

    /**
     * Loads one card.
     *
     * @param onBookmark      called when the Save button is pressed
     * @param onCompareToggle called with (scholarship, checked) when the checkbox toggles
     */
    public static ScholarshipCardController load(Scholarship scholarship, MatchResult match,
                                                 Consumer<Scholarship> onBookmark,
                                                 BiConsumer<Scholarship, Boolean> onCompareToggle) {
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
        controller.setScholarship(scholarship);
        controller.setMatch(match);
        return controller;
    }

    public Node getRoot() {
        return root;
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
        urgencyPill.setText(urgencyText(s.deadlineDate()));
        urgencyPill.getStyleClass().add(urgencyStyle(s.deadlineDate()));

        compareCheck.selectedProperty().addListener((obs, old, checked) ->
                onCompareToggle.accept(s, checked));
        saveButton.setOnAction(e -> {
            onBookmark.accept(s);
            saveButton.setText("Saved ✓");
            saveButton.setDisable(true);
        });
    }

    /** {@code null} = guest — badge hidden, caption prompts sign-in. */
    public void setMatch(MatchResult match) {
        if (match == null) {
            matchBadge.setVisible(false);
            matchBadge.setManaged(false);
            matchCaption.setText("Sign in for your match score");
            return;
        }
        matchBadge.setText(match.scoreLabel());
        matchBadge.getStyleClass().add(tierStyle(match.tier()));
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
