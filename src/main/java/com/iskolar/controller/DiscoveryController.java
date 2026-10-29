package com.iskolar.controller;

import com.iskolar.dao.DaoFactory;
import com.iskolar.dto.ComparisonRow;
import com.iskolar.dto.DiscoveryFilter;
import com.iskolar.dto.MatchResult;
import com.iskolar.model.DegreeProgram;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;
import com.iskolar.service.ServiceFactory;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ViewNavigator;
import com.iskolar.view.ComparisonDialog;
import com.iskolar.view.ScholarshipDetailsPanel;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guest-friendly discovery grid: filter bar (degree program, year level,
 * provider type, location), responsive card flow, per-card match badges,
 * bookmarking, and up-to-3 compare selection feeding the side-by-side modal.
 */
public class DiscoveryController {

    private static final int MAX_COMPARE = 3;
    /** Matches hgap in discovery.fxml. */
    private static final double CARD_HGAP = 16;
    /** Matches prefWidth in scholarship_card.fxml; cards never shrink below this. */
    private static final double CARD_MIN_WIDTH = 340;
    /** card-flow CSS padding: 24px left + 24px right. */
    private static final double CARD_FLOW_H_PADDING = 48;
    /** Small slack so pixel-snapped card widths never overflow a row and wrap early. */
    private static final double CARD_WIDTH_SLACK = 4;

    @FXML
    private ComboBox<DegreeProgram> programCombo;
    @FXML
    private ComboBox<Integer> yearCombo;
    @FXML
    private ComboBox<ProviderType> providerCombo;
    @FXML
    private TextField provinceField;
    @FXML
    private Button resetButton;
    @FXML
    private Label resultLabel;
    @FXML
    private Label selectionLabel;
    @FXML
    private Button compareButton;
    @FXML
    private HBox guestBanner;
    @FXML
    private Label bannerLabel;
    @FXML
    private Button bannerButton;
    @FXML
    private FlowPane cardPane;

    private final PauseTransition provinceDebounce = new PauseTransition(Duration.millis(350));
    private final Set<Long> compareSelection = new LinkedHashSet<>();
    private final Map<Long, ScholarshipCardController> cardControllers = new java.util.HashMap<>();
    private Map<Long, MatchResult> currentMatches = Map.of();

    @FXML
    private void initialize() {
        programCombo.getItems().addAll(DaoFactory.degreeProgramDao().findAll());
        programCombo.setConverter(anyConverter(DegreeProgram::displayLabel, "Any degree program"));
        yearCombo.getItems().addAll(List.of(1, 2, 3, 4, 5));
        yearCombo.setConverter(anyConverter(String::valueOf, "Any year level"));
        providerCombo.getItems().addAll(ProviderType.values());
        providerCombo.setConverter(anyConverter(ProviderType::displayName, "Any provider type"));

        programCombo.valueProperty().addListener((obs, old, val) -> refresh());
        yearCombo.valueProperty().addListener((obs, old, val) -> refresh());
        providerCombo.valueProperty().addListener((obs, old, val) -> refresh());
        provinceDebounce.setOnFinished(e -> refresh());
        provinceField.textProperty().addListener((obs, old, val) -> {
            provinceDebounce.stop();
            provinceDebounce.playFromStart();
        });
        resetButton.setOnAction(e -> {
            programCombo.setValue(null);
            yearCombo.setValue(null);
            providerCombo.setValue(null);
            provinceField.clear();
            refresh();
        });

        compareButton.setOnAction(e -> openComparison());

        // responsive grid: cards stretch to fill every row edge-to-edge
        cardPane.widthProperty().addListener((obs, oldVal, newVal) -> applyCardWidths());

        refresh();
    }

    /** Displays {@code "Any …"} for a {@code null} selection. */
    private static <T> StringConverter<T> anyConverter(Function<T, String> label, String anyLabel) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? anyLabel : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null; // combo not editable
            }
        };
    }

    private void refresh() {
        DiscoveryFilter filter = new DiscoveryFilter(
                programCombo.getValue() == null ? null : programCombo.getValue().programId(),
                yearCombo.getValue(),
                provinceField.getText(),
                providerCombo.getValue());
        List<Scholarship> scholarships = DaoFactory.scholarshipDao().findActiveForDiscovery(filter);
        currentMatches = loadMatches();

        compareSelection.removeIf(id -> scholarships.stream().noneMatch(s -> s.scholarshipId() == id));

        cardPane.getChildren().clear();
        cardControllers.clear();
        for (Scholarship scholarship : scholarships) {
            ScholarshipCardController controller = ScholarshipCardController.load(
                    scholarship,
                    currentMatches.get(scholarship.scholarshipId()),
                    this::bookmark,
                    this::onCompareToggle,
                    this::openDetails);
            cardPane.getChildren().add(controller.getRoot());
            cardControllers.put(scholarship.scholarshipId(), controller);
        }
        applyCardWidths();
        resultLabel.setText(scholarships.size()
                + (scholarships.size() == 1 ? " scholarship found" : " scholarships found"));
        updateCompareState();
        updateBanner();
    }

    /**
     * Stretches every card so each row of the flow pane spans the full
     * available width — no blank strip along the right edge of the grid.
     */
    private void applyCardWidths() {
        double available = cardPane.getWidth() - CARD_FLOW_H_PADDING;
        if (available <= CARD_MIN_WIDTH) {
            return; // viewport still settling; keep natural card width
        }
        int columns = (int) Math.max(1,
                Math.floor((available + CARD_HGAP) / (CARD_MIN_WIDTH + CARD_HGAP)));
        double cardWidth = (available - (columns - 1) * CARD_HGAP - CARD_WIDTH_SLACK) / columns;
        for (Node node : cardPane.getChildren()) {
            if (node instanceof Region region) {
                region.setPrefWidth(cardWidth);
            }
        }
    }

    /** Match scores for the logged-in student, keyed by scholarship id (empty for guests). */
    private Map<Long, MatchResult> loadMatches() {
        var session = SessionManager.current();
        if (session.isEmpty()) {
            return Map.of();
        }
        var profile = DaoFactory.studentProfileDao().findByUserId(session.get().userId());
        if (profile.isEmpty()) {
            return Map.of();
        }
        return ServiceFactory.matchingService().matchAll(profile.get().studentId()).stream()
                .collect(Collectors.toMap(MatchResult::scholarshipId, r -> r));
    }

    // ------------------------------------------------------------------
    // Bookmark + compare + details
    // ------------------------------------------------------------------

    /** Opens the full scholarship details in the in-app side panel. */
    private void openDetails(Scholarship scholarship) {
        var bundle = DaoFactory.scholarshipDao()
                .findEligibilityBundle(scholarship.scholarshipId())
                .orElse(null);
        if (bundle == null) {
            return;
        }
        ScholarshipDetailsPanel.show(ViewNavigator.centerHost(), bundle,
                currentMatches.get(scholarship.scholarshipId()));
    }

    private void bookmark(Scholarship scholarship) {
        var session = SessionManager.current();
        if (session.isEmpty()) {
            ViewNavigator.show(ViewNavigator.LOGIN_VIEW);
            return;
        }
        var profile = DaoFactory.studentProfileDao().findByUserId(session.get().userId());
        if (profile.isEmpty()) {
            resultLabel.setText("Complete your profile wizard before saving scholarships.");
            return;
        }
        ServiceFactory.applicationService().bookmark(profile.get().studentId(), scholarship.scholarshipId());
        resultLabel.setText("Saved to your tracker: " + scholarship.title());
    }

    private void onCompareToggle(Scholarship scholarship, boolean checked) {
        if (checked) {
            if (compareSelection.size() >= MAX_COMPARE) {
                return;
            }
            compareSelection.add(scholarship.scholarshipId());
        } else {
            compareSelection.remove(scholarship.scholarshipId());
        }
        updateCompareState();
    }

    private void updateCompareState() {
        boolean locked = compareSelection.size() >= MAX_COMPARE;
        cardControllers.forEach((id, controller) ->
                controller.setCompareLocked(locked && !compareSelection.contains(id)));
        compareButton.setText("Compare (" + compareSelection.size() + ")");
        compareButton.setDisable(compareSelection.size() < 2);
        selectionLabel.setVisible(!compareSelection.isEmpty());
        selectionLabel.setManaged(!compareSelection.isEmpty());
        if (!compareSelection.isEmpty()) {
            selectionLabel.setText(compareSelection.size() + " selected (max " + MAX_COMPARE + ")");
        }
    }

    private void openComparison() {
        List<ComparisonRow> rows = new ArrayList<>();
        for (Long id : compareSelection) {
            var scholarship = DaoFactory.scholarshipDao().findById(id).orElse(null);
            if (scholarship == null) {
                continue;
            }
            ScholarshipCriteria criteria = DaoFactory.scholarshipDao().findCriteria(id)
                    .orElse(new ScholarshipCriteria(0, id, null, true, null, null, null, null));
            rows.add(new ComparisonRow(scholarship, criteria));
        }
        ComparisonDialog.show(cardPane.getScene().getWindow(), rows);
    }

    private void updateBanner() {
        var session = SessionManager.current();
        boolean hasProfile = session.isPresent()
                && DaoFactory.studentProfileDao().findByUserId(session.get().userId()).isPresent();
        guestBanner.setVisible(!hasProfile);
        guestBanner.setManaged(!hasProfile);
        if (session.isEmpty()) {
            bannerLabel.setText("Sign in to see your personalized match score on every card.");
            bannerButton.setText("Log in");
            bannerButton.setOnAction(e -> ViewNavigator.show(ViewNavigator.LOGIN_VIEW));
        } else {
            bannerLabel.setText("Complete your profile wizard to unlock personalized match scores.");
            bannerButton.setText("Open wizard");
            bannerButton.setOnAction(e -> ViewNavigator.show(ViewNavigator.WIZARD_VIEW));
        }
    }
}
