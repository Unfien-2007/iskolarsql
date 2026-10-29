package com.iskolar.view;

import com.iskolar.dao.DaoFactory;
import com.iskolar.dto.MatchResult;
import com.iskolar.dto.ScholarshipEligibilityBundle;
import com.iskolar.dto.TagRequirement;
import com.iskolar.model.DeadlineUrgency;
import com.iskolar.model.DegreeProgram;
import com.iskolar.model.DocumentType;
import com.iskolar.model.MatchTier;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;
import com.iskolar.util.GwaConverter;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * In-app side panel showing one scholarship's full details: match verdict,
 * description, financials, key dates, eligibility gates, allowed programs,
 * tags, required documents and the application link.
 *
 * <p>The panel slides in over the center content area (next to the sidebar,
 * under the journey rail) instead of opening a separate window. Close with the
 * header ✕, {@code Esc}, or by clicking anywhere outside the panel; navigation
 * closes it automatically.</p>
 */
public final class ScholarshipDetailsPanel {

    private static final double WIDTH = 460;

    private static StackPane host;
    private static StackPane layer;
    private static VBox panel;
    private static EventHandler<KeyEvent> escHandler;
    private static EventHandler<MouseEvent> outsideHandler;
    private static Scene escScene;
    private static boolean closing;

    private ScholarshipDetailsPanel() {
    }

    /**
     * Opens (or refreshes) the details panel inside the given host.
     *
     * @param host   the center content area from {@code ViewNavigator.centerHost()}
     * @param bundle the full eligibility read-model for the scholarship
     * @param match  the signed-in student's match result, or {@code null} for guests
     */
    public static void show(StackPane host, ScholarshipEligibilityBundle bundle, MatchResult match) {
        if (host == null || bundle == null || bundle.scholarship() == null) {
            return;
        }
        // Already open here: just swap the scrollable body, no second slide-in.
        if (layer != null && layer.getParent() == host && panel != null && panel.getChildren().size() > 1) {
            panel.getChildren().set(1, buildScroll(bundle, match));
            return;
        }
        dismiss();

        ScholarshipDetailsPanel.host = host;
        closing = false;

        panel = new VBox(buildHeader(), buildScroll(bundle, match));
        panel.getStyleClass().add("details-drawer");
        panel.setPrefWidth(WIDTH);
        panel.setMaxWidth(WIDTH);
        panel.setMaxHeight(Double.MAX_VALUE);
        panel.setMinHeight(0);
        panel.setTranslateX(WIDTH);
        panel.setOpacity(0);

        layer = new StackPane(panel);
        layer.getStyleClass().add("details-drawer-layer");
        layer.setPickOnBounds(true); // catches clicks outside the panel so they close it
        layer.setMinSize(0, 0);
        layer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(panel, Pos.CENTER_RIGHT);
        host.getChildren().add(layer);

        Scene scene = host.getScene();
        if (scene != null) {
            escScene = scene;
            escHandler = e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    close();
                    e.consume();
                }
            };
            scene.addEventFilter(KeyEvent.KEY_PRESSED, escHandler);
            outsideHandler = e -> {
                boolean inside = e.getTarget() instanceof Node target && isInside(panel, target);
                if (!inside) {
                    close();
                }
            };
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideHandler);
        }

        TranslateTransition slide = new TranslateTransition(Duration.millis(280), panel);
        slide.setToX(0);
        slide.setInterpolator(Motion.SPLINE);
        FadeTransition fade = new FadeTransition(Duration.millis(220), panel);
        fade.setToValue(1);
        new ParallelTransition(slide, fade).play();
    }

    /** Removes the panel immediately (used on navigation). */
    public static void dismiss() {
        closing = false;
        if (escHandler != null && escScene != null) {
            escScene.removeEventFilter(KeyEvent.KEY_PRESSED, escHandler);
        }
        if (outsideHandler != null && escScene != null) {
            escScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideHandler);
        }
        escHandler = null;
        outsideHandler = null;
        escScene = null;
        if (layer != null && layer.getParent() instanceof Pane parent) {
            parent.getChildren().remove(layer);
        }
        layer = null;
        panel = null;
        host = null;
    }

    private static void close() {
        if (layer == null || panel == null || closing) {
            return;
        }
        closing = true;
        TranslateTransition slide = new TranslateTransition(Duration.millis(200), panel);
        slide.setToX(WIDTH);
        slide.setInterpolator(Motion.SPLINE);
        FadeTransition fade = new FadeTransition(Duration.millis(160), panel);
        fade.setToValue(0);
        ParallelTransition out = new ParallelTransition(slide, fade);
        out.setOnFinished(e -> dismiss());
        out.play();
    }

    // ------------------------------------------------------------------
    // Chrome
    // ------------------------------------------------------------------

    private static HBox buildHeader() {
        Label eyebrow = new Label("SCHOLARSHIP DETAILS");
        eyebrow.getStyleClass().add("details-drawer-eyebrow");
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeButton = new Button();
        closeButton.setGraphic(FontIcon.of(Feather.X, 16));
        closeButton.getStyleClass().add("dialog-close");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(e -> close());
        HBox header = new HBox(10, eyebrow, spacer, closeButton);
        header.getStyleClass().add("details-drawer-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private static ScrollPane buildScroll(ScholarshipEligibilityBundle bundle, MatchResult match) {
        Scholarship scholarship = bundle.scholarship();
        VBox content = new VBox(10);
        content.getStyleClass().add("details-root");
        content.getChildren().addAll(
                header(scholarship),
                matchSection(match),
                section("About this scholarship", description(scholarship)),
                section("Financial support",
                        row("Tuition coverage", valueOrDash(scholarship.tuitionBenefit())),
                        row("Monthly stipend", scholarship.stipendLabel()),
                        row("Return of service", returnService(scholarship.returnServiceYears()))),
                section("Key dates",
                        row("Applications open", dateOrDash(scholarship.openDate())),
                        row("Deadline", dateOrDash(scholarship.deadlineDate()))),
                eligibilitySection(bundle),
                programsSection(bundle),
                tagsSection(bundle),
                documentsSection(bundle),
                applySection(scholarship));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("details-scroll");
        scroll.setMinHeight(0);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private static VBox header(Scholarship s) {
        Label badge = new Label(s.providerType().displayName());
        badge.getStyleClass().addAll("badge", providerStyle(s.providerType()));
        Label pill = new Label(urgencyText(s.deadlineDate()));
        pill.getStyleClass().addAll("pill", urgencyStyle(s.deadlineDate()));
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, badge, spacer, pill);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(s.title());
        title.getStyleClass().add("details-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        Label provider = new Label("Offered by " + s.providerName());
        provider.getStyleClass().add("details-provider");
        provider.setWrapText(true);
        return new VBox(6, top, title, provider);
    }

    private static VBox matchSection(MatchResult match) {
        if (match == null) {
            return section("Your match", note("Sign in to see your personalized match score."));
        }
        Label badge = new Label(match.scoreLabel());
        badge.getStyleClass().addAll("match-badge", tierStyle(match.tier()));
        Label caption = new Label(match.eligible()
                ? match.tier().label() + (match.borderlineGrade() ? " · Borderline GWA" : "")
                : "Not eligible");
        caption.getStyleClass().add("details-value");

        VBox box = section("Your match", new HBox(10, badge, caption));
        if (!match.eligible()) {
            Label gate = new Label(match.gateReason());
            gate.getStyleClass().add("details-warning");
            gate.setWrapText(true);
            box.getChildren().add(gate);
        }
        for (String warning : match.warnings()) {
            Label line = new Label("• " + warning);
            line.getStyleClass().add("details-warning");
            line.setWrapText(true);
            box.getChildren().add(line);
        }
        return box;
    }

    private static VBox eligibilitySection(ScholarshipEligibilityBundle bundle) {
        ScholarshipCriteria c = bundle.criteria();
        String gwa = c == null || c.minGwaPercentage() == null
                ? "None required"
                : "≥ " + GwaConverter.formatPercent(c.minGwaPercentage())
                + "  (GWA " + String.format(Locale.ROOT, "%.2f",
                GwaConverter.percentToFivePoint(c.minGwaPercentage())) + " in 5.00 scale)";
        String failing = c != null && c.allowFailingGrade() ? "Allowed" : "Not allowed";
        String income = c == null || c.maxAnnualIncome() == null
                ? "No cap — merit-based only"
                : "PHP " + String.format(Locale.ROOT, "%,.0f", c.maxAnnualIncome());
        String school = c == null || c.allowedSchoolType() == null
                ? "Public and private schools"
                : c.allowedSchoolType().displayName() + " schools only";
        String residency = c == null || c.requiredProvince() == null
                ? "Open nationwide"
                : "Restricted to " + c.requiredProvince()
                + (c.requiredCity() == null ? "" : ", " + c.requiredCity());
        return section("Eligibility",
                row("Minimum GWA", gwa),
                row("Failing grades", failing),
                row("Income cap", income),
                row("School type", school),
                row("Residency", residency));
    }

    private static VBox programsSection(ScholarshipEligibilityBundle bundle) {
        String value;
        if (bundle.allowedProgramIds().isEmpty()) {
            value = "All degree programs";
        } else {
            value = DaoFactory.degreeProgramDao().findAll().stream()
                    .filter(p -> bundle.allowedProgramIds().contains(p.programId()))
                    .map(DegreeProgram::displayLabel)
                    .collect(Collectors.joining(", "));
        }
        return section("Degree programs", row("Allowed programs", value));
    }

    private static VBox tagsSection(ScholarshipEligibilityBundle bundle) {
        List<String> mandatory = bundle.tagRequirements().stream()
                .filter(TagRequirement::mandatory)
                .map(r -> r.tag().tagName())
                .toList();
        List<String> preferred = bundle.tagRequirements().stream()
                .filter(r -> !r.mandatory())
                .map(r -> r.tag().tagName())
                .toList();
        if (mandatory.isEmpty() && preferred.isEmpty()) {
            return section("Tags", note("No tag requirements."));
        }
        List<Node> rows = new ArrayList<>();
        if (!mandatory.isEmpty()) {
            rows.add(row("Required", String.join(", ", mandatory)));
        }
        if (!preferred.isEmpty()) {
            rows.add(row("Preferred", String.join(", ", preferred)));
        }
        return section("Tags", rows.toArray(Node[]::new));
    }

    private static VBox documentsSection(ScholarshipEligibilityBundle bundle) {
        String value;
        if (bundle.requiredDocumentIds().isEmpty()) {
            value = "No document requirements.";
        } else {
            value = DaoFactory.documentDao().findAll().stream()
                    .filter(d -> bundle.requiredDocumentIds().contains(d.documentTypeId()))
                    .map(DocumentType::docName)
                    .collect(Collectors.joining(", "));
        }
        return section("Required documents", row("Documents", value));
    }

    private static VBox applySection(Scholarship s) {
        String url = s.applicationUrl() == null ? "" : s.applicationUrl().trim();
        if (url.isEmpty()) {
            return section("Apply", note("No application link provided."));
        }
        Hyperlink link = new Hyperlink(url);
        link.getStyleClass().add("details-link");
        link.setWrapText(true);
        link.setOnAction(e -> openInBrowser(url));
        return section("Apply", row("Application link", link));
    }

    // ------------------------------------------------------------------
    // Small builders + formatters
    // ------------------------------------------------------------------

    private static VBox section(String title, Node... rows) {
        Label heading = new Label(title.toUpperCase(Locale.ROOT));
        heading.getStyleClass().add("details-section-title");
        VBox box = new VBox(6);
        box.getStyleClass().add("details-section");
        box.getChildren().add(heading);
        box.getChildren().addAll(rows);
        return box;
    }

    private static HBox row(String attribute, String value) {
        Label val = new Label(value);
        val.getStyleClass().add("details-value");
        val.setWrapText(true);
        return row(attribute, (Node) val);
    }

    private static HBox row(String attribute, Node value) {
        Label attr = new Label(attribute);
        attr.getStyleClass().add("details-attr");
        HBox box = new HBox(10, attr, value);
        if (value instanceof javafx.scene.layout.Region region) {
            region.setMinWidth(0);
            region.maxWidthProperty().bind(
                    box.widthProperty().subtract(attr.widthProperty()).subtract(10));
        }
        box.getStyleClass().add("details-row");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Label description(Scholarship s) {
        String text = s.description() == null || s.description().isBlank()
                ? "No description provided." : s.description();
        Label label = new Label(text);
        label.getStyleClass().add("details-desc");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("details-note");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static boolean isInside(Node ancestor, Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static void openInBrowser(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Open this link in your browser:\n\n" + url, ButtonType.OK);
            alert.setTitle("Application link");
            alert.showAndWait();
        }
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String dateOrDash(LocalDate date) {
        return date == null ? "—" : date.toString();
    }

    private static String returnService(int years) {
        return years <= 0 ? "None" : years + (years == 1 ? " year" : " years");
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
