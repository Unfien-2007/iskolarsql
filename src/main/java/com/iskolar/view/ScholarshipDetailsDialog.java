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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Read-only details modal for one scholarship: match verdict, description,
 * financials, key dates, eligibility gates, allowed programs/year levels,
 * tags, required documents and an externally-opening application link.
 * Opened by clicking a discovery card or a tracker card.
 */
public final class ScholarshipDetailsDialog {

    private ScholarshipDetailsDialog() {
    }

    /**
     * @param bundle the full eligibility read-model for the scholarship
     * @param match  the signed-in student's match result, or {@code null} for guests
     */
    public static void show(Window owner, ScholarshipEligibilityBundle bundle, MatchResult match) {
        if (bundle == null || bundle.scholarship() == null) {
            return;
        }
        Scholarship scholarship = bundle.scholarship();

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(scholarship.title());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                ScholarshipDetailsDialog.class.getResource("/css/app.css"),
                "css/app.css missing").toExternalForm());

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
        scroll.setPrefViewportHeight(540);

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setMinWidth(560);
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private static VBox header(Scholarship s) {
        Label badge = new Label(s.providerType().displayName());
        badge.getStyleClass().addAll("badge", providerStyle(s.providerType()));
        Label pill = new Label(urgencyText(s.deadlineDate()));
        pill.getStyleClass().addAll("pill", urgencyStyle(s.deadlineDate()));
        Region spacer = new Region();
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
        return section("Apply", link);
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
        Label attr = new Label(attribute);
        attr.getStyleClass().add("details-attr");
        Label val = new Label(value);
        val.getStyleClass().add("details-value");
        val.setWrapText(true);
        val.setMaxWidth(Double.MAX_VALUE);
        HBox box = new HBox(10, attr, val);
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
