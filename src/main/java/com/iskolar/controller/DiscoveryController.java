package com.iskolar.controller;

import com.iskolar.dao.DaoFactory;
import com.iskolar.dto.ApplicationCard;
import com.iskolar.dto.ComparisonRow;
import com.iskolar.dto.DiscoveryFilter;
import com.iskolar.dto.MatchResult;
import com.iskolar.model.DegreeProgram;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;
import com.iskolar.service.ServiceFactory;
import com.iskolar.service.SyncService;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ViewNavigator;
import com.iskolar.view.ComparisonPanel;
import com.iskolar.view.ScholarshipDetailsPanel;
import com.iskolar.view.Snackbar;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
    /** Matches prefWidth in scholarship_card.fxml; cards never shrink below this.
     *  At 320 a 1280px window (1060px center) lays out three columns instead of two. */
    private static final double CARD_MIN_WIDTH = 320;
    /** Upper bound so narrow windows don't stretch a single card across the viewport. */
    private static final double CARD_MAX_WIDTH = 460;
    /** card-flow CSS padding: 24px left + 24px right. */
    private static final double CARD_FLOW_H_PADDING = 48;
    /** Small slack so pixel-snapped card widths never overflow a row and wrap early. */
    private static final double CARD_WIDTH_SLACK = 4;

    /**
     * Compare ticks survive navigation and filter changes, so opening the
     * details panel or the tracker never silently clears a comparison the
     * student was building. Cleared explicitly, or when the sheet is closed
     * after the comparison runs.
     */
    private static final Set<Long> COMPARE_SELECTION = new LinkedHashSet<>();

    /** Client-side orderings applied after the DAO query. */
    private enum SortOrder {
        RECOMMENDED("Recommended"),
        DEADLINE("Deadline soonest"),
        MATCH("Match score"),
        TITLE("Title A–Z");

        private final String label;

        SortOrder(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<DegreeProgram> programCombo;
    @FXML
    private ComboBox<Integer> yearCombo;
    @FXML
    private ComboBox<ProviderType> providerCombo;
    @FXML
    private TextField provinceField;
    @FXML
    private CheckBox eligibleOnlyCheck;
    @FXML
    private Button resetButton;
    @FXML
    private Label resultLabel;
    @FXML
    private Label selectionLabel;
    @FXML
    private Button compareButton;
    @FXML
    private ComboBox<SortOrder> sortCombo;
    @FXML
    private HBox guestBanner;
    @FXML
    private Label bannerLabel;
    @FXML
    private Button bannerButton;
    @FXML
    private Label syncLabel;
    @FXML
    private Button syncButton;
    @FXML
    private FlowPane cardPane;
    @FXML
    private VBox emptyState;
    @FXML
    private Label emptyLabel;
    @FXML
    private Button emptyResetButton;

    private final PauseTransition provinceDebounce = new PauseTransition(Duration.millis(350));
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(300));
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
        searchDebounce.setOnFinished(e -> refresh());
        searchField.textProperty().addListener((obs, old, val) -> {
            searchDebounce.stop();
            searchDebounce.playFromStart();
        });
        eligibleOnlyCheck.selectedProperty().addListener((obs, old, val) -> refresh());
        // "Eligible only" needs a personalized match verdict; guests never see it.
        eligibleOnlyCheck.setVisible(false);
        eligibleOnlyCheck.setManaged(false);
        emptyResetButton.setOnAction(e -> clearFilters());
        resetButton.setOnAction(e -> clearFilters());

        // Result ordering is a view concern, so it is applied client-side.
        sortCombo.getItems().addAll(SortOrder.values());
        sortCombo.setValue(SortOrder.RECOMMENDED);
        sortCombo.valueProperty().addListener((obs, old, val) -> refresh());

        compareButton.setOnAction(e -> openComparison());
        compareButton.setTooltip(new Tooltip("Tick Compare on at least two scholarships"));

        // Esc clears a search without reaching for the mouse.
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && !searchField.getText().isEmpty()) {
                searchField.clear();
                e.consume();
            }
        });
        searchField.setTooltip(new Tooltip("Search by title or provider (Ctrl+F)"));

        // catalog sync indicator
        syncButton.setOnAction(e -> ServiceFactory.syncService().syncInBackground());
        ServiceFactory.syncService().addListener(this::onSyncStatus);
        showLocalSyncState();

        // responsive grid: cards stretch to fill every row edge-to-edge
        cardPane.widthProperty().addListener((obs, oldVal, newVal) -> applyCardWidths());

        refresh();
    }

    private void showLocalSyncState() {
        var service = ServiceFactory.syncService();
        syncLabel.setText(service.localVersion() > 0
                ? "Offline copy of the catalog · " + service.updatedText()
                : "Using the built-in catalog · not synced yet");
        syncLabel.getStyleClass().remove("offline");
    }

    private void onSyncStatus(SyncService.SyncResult result) {
        var service = ServiceFactory.syncService();
        if (result.syncing()) {
            syncLabel.setText("Syncing catalog…");
            syncLabel.getStyleClass().remove("offline");
            return;
        }
        if (!result.success()) {
            syncLabel.setText("Offline · showing the saved catalog · " + service.updatedText());
            if (!syncLabel.getStyleClass().contains("offline")) {
                syncLabel.getStyleClass().add("offline");
            }
            return;
        }
        syncLabel.getStyleClass().remove("offline");
        syncLabel.setText(result.changed()
                ? "Catalog updated · version " + result.version() + " · " + service.updatedText()
                : "Catalog up to date · version " + result.version() + " · " + service.updatedText());
        if (result.changed()) {
            refresh(); // re-render the grid against the fresh catalog
        }
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
                providerCombo.getValue(),
                searchField.getText());
        currentMatches = loadMatches();
        boolean personalized = !currentMatches.isEmpty();
        eligibleOnlyCheck.setVisible(personalized);
        eligibleOnlyCheck.setManaged(personalized);
        Set<Long> savedIds = loadSavedIds();

        List<Scholarship> found = DaoFactory.scholarshipDao().findActiveForDiscovery(filter);
        // Client-side gate: the DAO only knows the catalog, not this student's verdict.
        List<Scholarship> eligible = personalized && eligibleOnlyCheck.isSelected()
                ? found.stream().filter(s -> isEligible(currentMatches.get(s.scholarshipId()))).toList()
                : found;
        List<Scholarship> scholarships = sorted(eligible);

        cardPane.getChildren().clear();
        cardControllers.clear();
        for (Scholarship scholarship : scholarships) {
            long id = scholarship.scholarshipId();
            ScholarshipCardController controller = ScholarshipCardController.load(
                    scholarship,
                    currentMatches.get(id),
                    savedIds.contains(id),
                    this::bookmark,
                    this::onCompareToggle,
                    this::openDetails);
            // Re-apply a comparison tick that survived navigation or a filter change.
            controller.setCompareSelected(COMPARE_SELECTION.contains(id));
            cardPane.getChildren().add(controller.getRoot());
            cardControllers.put(id, controller);
        }
        applyCardWidths();
        resultLabel.setText(scholarships.size()
                + (scholarships.size() == 1 ? " scholarship found" : " scholarships found"));
        resetButton.setDisable(!hasActiveFilters());
        updateEmptyState(scholarships.isEmpty());
        updateCompareState();
        updateBanner();
    }

    /** Applies the toolbar ordering; the DAO order stays the default "Recommended". */
    private List<Scholarship> sorted(List<Scholarship> list) {
        SortOrder order = sortCombo == null || sortCombo.getValue() == null
                ? SortOrder.RECOMMENDED : sortCombo.getValue();
        if (order == SortOrder.RECOMMENDED) {
            return list;
        }
        List<Scholarship> copy = new ArrayList<>(list);
        switch (order) {
            case DEADLINE -> copy.sort(DEADLINE_ORDER);
            case MATCH -> copy.sort(Comparator
                    .comparingDouble((Scholarship s) -> scoreOf(currentMatches.get(s.scholarshipId())))
                    .reversed()
                    .thenComparing(Scholarship::title, String.CASE_INSENSITIVE_ORDER));
            case TITLE -> copy.sort(Comparator.comparing(Scholarship::title,
                    String.CASE_INSENSITIVE_ORDER));
            default -> { }
        }
        return copy;
    }

    /** Score of a match result, or -1 when the card carries no verdict. */
    private static double scoreOf(MatchResult match) {
        return match == null ? -1 : match.score();
    }

    /**
     * Open deadlines first, passed ones last, and undated entries after dated
     * ones — so "Deadline soonest" never leads with scholarship opportunities
     * that already closed.
     */
    private static final Comparator<Scholarship> DEADLINE_ORDER = (a, b) -> {
        LocalDate left = a.deadlineDate();
        LocalDate right = b.deadlineDate();
        if (left == null || right == null) {
            return left == null ? (right == null ? 0 : 1) : -1;
        }
        LocalDate today = LocalDate.now();
        boolean leftPassed = left.isBefore(today);
        boolean rightPassed = right.isBefore(today);
        if (leftPassed != rightPassed) {
            return leftPassed ? 1 : -1;
        }
        return left.compareTo(right);
    };

    /** A card counts as eligible only when it carries a scored, passing verdict. */
    private static boolean isEligible(MatchResult match) {
        return match != null && match.eligible();
    }

    /** Clears every filter and re-queries once. */
    private void clearFilters() {
        searchField.clear();
        programCombo.setValue(null);
        yearCombo.setValue(null);
        providerCombo.setValue(null);
        provinceField.clear();
        eligibleOnlyCheck.setSelected(false);
        refresh();
    }

    /** True when at least one filter is narrowing the catalog. */
    private boolean hasActiveFilters() {
        return (searchField.getText() != null && !searchField.getText().isBlank())
                || programCombo.getValue() != null
                || yearCombo.getValue() != null
                || providerCombo.getValue() != null
                || (provinceField.getText() != null && !provinceField.getText().isBlank())
                || (eligibleOnlyCheck.isVisible() && eligibleOnlyCheck.isSelected());
    }

    /** Swaps the blank grid for an explanatory empty state (and back). */
    private void updateEmptyState(boolean empty) {
        emptyState.setVisible(empty);
        cardPane.setVisible(!empty);
        if (!empty) {
            return;
        }
        boolean filtered = hasActiveFilters();
        emptyLabel.setText(filtered
                ? "No open scholarship matches these filters. Try a broader keyword or clear one."
                : "There are no open scholarships in the catalog right now. "
                        + "Refresh the catalog above to check for new postings.");
        emptyResetButton.setVisible(filtered);
        emptyResetButton.setManaged(filtered);
    }

    /**
     * Stretches every card so each row of the flow pane spans the full
     * available width — no blank strip along the right edge of the grid.
     * Width is capped at {@link #CARD_MAX_WIDTH} so a one- or two-column
     * viewport yields readable, left-aligned cards instead of one very wide card.
     */
    private void applyCardWidths() {
        double available = cardPane.getWidth() - CARD_FLOW_H_PADDING;
        if (available <= CARD_MIN_WIDTH) {
            return; // viewport still settling; keep natural card width
        }
        int columns = (int) Math.max(1,
                Math.floor((available + CARD_HGAP) / (CARD_MIN_WIDTH + CARD_HGAP)));
        double cardWidth = Math.min(CARD_MAX_WIDTH,
                (available - (columns - 1) * CARD_HGAP - CARD_WIDTH_SLACK) / columns);
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

    /**
     * Scholarship ids already on the student's tracker — any tracker entry
     * (saved, applied, awarded…) means the card must open in the saved state.
     */
    private Set<Long> loadSavedIds() {
        var session = SessionManager.current();
        if (session.isEmpty()) {
            return Set.of();
        }
        var profile = DaoFactory.studentProfileDao().findByUserId(session.get().userId());
        if (profile.isEmpty()) {
            return Set.of();
        }
        Set<Long> saved = new LinkedHashSet<>();
        for (ApplicationCard card : ServiceFactory.applicationService()
                .cardsForStudent(profile.get().studentId())) {
            saved.add(card.scholarship().scholarshipId());
        }
        return saved;
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

    /**
     * Bookmarks the scholarship onto the student's tracker.
     *
     * @return {@code true} when the card was persisted (guests bounce to login,
     *         profile-less students get a hint instead of a false "Saved")
     */
    private boolean bookmark(Scholarship scholarship) {
        var session = SessionManager.current();
        if (session.isEmpty()) {
            ViewNavigator.show(ViewNavigator.LOGIN_VIEW);
            return false;
        }
        var profile = DaoFactory.studentProfileDao().findByUserId(session.get().userId());
        if (profile.isEmpty()) {
            Snackbar.show("Complete your profile wizard before saving scholarships.",
                    "Open wizard", () -> ViewNavigator.show(ViewNavigator.WIZARD_VIEW));
            return false;
        }
        ServiceFactory.applicationService().bookmark(profile.get().studentId(), scholarship.scholarshipId());
        // A snackbar confirms the save without overwriting the "N scholarships
        // found" counter the grid uses for orientation.
        Snackbar.show("Saved \"" + scholarship.title() + "\" to your tracker",
                "View tracker", () -> ViewNavigator.show(ViewNavigator.TRACKER_VIEW));
        return true;
    }

    private void onCompareToggle(Scholarship scholarship, boolean checked) {
        if (checked) {
            if (COMPARE_SELECTION.size() >= MAX_COMPARE) {
                return;
            }
            COMPARE_SELECTION.add(scholarship.scholarshipId());
        } else {
            COMPARE_SELECTION.remove(scholarship.scholarshipId());
        }
        updateCompareState();
    }

    private void updateCompareState() {
        int count = COMPARE_SELECTION.size();
        boolean locked = count >= MAX_COMPARE;
        cardControllers.forEach((id, controller) ->
                controller.setCompareLocked(locked && !COMPARE_SELECTION.contains(id)));
        compareButton.setText("Compare (" + count + ")");
        compareButton.setDisable(count < 2);
        if (compareButton.getTooltip() != null) {
            compareButton.getTooltip().setText(count < 2
                    ? "Tick Compare on at least two scholarships"
                    : "Open the side-by-side comparison");
        }
        selectionLabel.setVisible(count > 0);
        selectionLabel.setManaged(count > 0);
        if (count > 0) {
            selectionLabel.setText(count + " selected (max " + MAX_COMPARE + ")");
        }
    }

    /** Sidebar "Compare": opens the sheet when ready, otherwise explains the next step. */
    public void openCompareFromSidebar() {
        if (COMPARE_SELECTION.size() >= 2) {
            openComparison();
        } else {
            Snackbar.show("Tick Compare on two or three scholarships to see them side by side.",
                    null, null);
        }
    }

    /** Drops persisted compare ticks — called on logout so they never leak between accounts. */
    public static void clearCompareSelection() {
        COMPARE_SELECTION.clear();
    }

    private void openComparison() {
        List<ComparisonRow> rows = new ArrayList<>();
        for (Long id : COMPARE_SELECTION) {
            var scholarship = DaoFactory.scholarshipDao().findById(id).orElse(null);
            if (scholarship == null) {
                continue;
            }
            ScholarshipCriteria criteria = DaoFactory.scholarshipDao().findCriteria(id)
                    .orElse(new ScholarshipCriteria(0, id, null, true, null, null, null, null));
            rows.add(new ComparisonRow(scholarship, criteria, currentMatches.get(id)));
        }
        if (rows.size() < 2) {
            Snackbar.show("That scholarship is no longer in the catalog — pick another to compare.",
                    null, null);
            return;
        }
        ComparisonPanel.show(ViewNavigator.centerHost(), rows);
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
