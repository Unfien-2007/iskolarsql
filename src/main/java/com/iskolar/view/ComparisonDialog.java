package com.iskolar.view;

import com.iskolar.dto.ComparisonRow;
import com.iskolar.util.GwaConverter;
import com.iskolar.util.ThemeManager;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Side-by-side comparison modal: up to 3 scholarships against tuition
 * coverage, monthly stipend, maintaining GWA, and return-of-service.
 */
public final class ComparisonDialog {

    private ComparisonDialog() {
    }

    /** Requires at least 2 rows to be meaningful. */
    public static void show(Window owner, List<ComparisonRow> rows) {
        if (rows == null || rows.size() < 2) {
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Compare Scholarships");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                ComparisonDialog.class.getResource("/css/app.css"),
                "css/app.css missing").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-root");
        if (ThemeManager.isDark()) {
            dialog.getDialogPane().getStyleClass().add("dark");
        }

        GridPane grid = new GridPane();
        grid.getStyleClass().add("compare-grid");
        grid.setHgap(16);
        grid.setVgap(10);

        grid.add(new Label(""), 0, 0);
        for (int i = 0; i < rows.size(); i++) {
            Label title = new Label(rows.get(i).scholarship().title());
            title.getStyleClass().add("compare-title");
            title.setWrapText(true);
            title.setMaxWidth(230);
            grid.add(title, i + 1, 0);
        }

        addRow(grid, rows, 1, "Provider", r -> r.scholarship().providerName());
        addRow(grid, rows, 2, "Provider Type", r -> r.scholarship().providerType().displayName());
        addRow(grid, rows, 3, "Tuition Coverage", r -> valueOrDash(r.scholarship().tuitionBenefit()));
        addRow(grid, rows, 4, "Monthly Stipend", r -> r.scholarship().stipendLabel());
        addRow(grid, rows, 5, "Maintaining GWA", r -> r.criteria().minGwaPercentage() == null
                ? "None required"
                : "≥ " + GwaConverter.formatPercent(r.criteria().minGwaPercentage()));
        addRow(grid, rows, 6, "Return of Service", r -> {
            int years = r.scholarship().returnServiceYears();
            return years <= 0 ? "None" : years + (years == 1 ? " year" : " years");
        });
        addRow(grid, rows, 7, "Deadline", r -> r.scholarship().deadlineDate() == null
                ? "—" : r.scholarship().deadlineDate().toString());

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(Math.min(260 + rows.size() * 260, 900));
        dialog.showAndWait();
    }

    private static void addRow(GridPane grid, List<ComparisonRow> rows, int rowIndex,
                               String attribute, Function<ComparisonRow, String> value) {
        Label label = new Label(attribute);
        label.getStyleClass().add("compare-attr");
        grid.add(label, 0, rowIndex);
        for (int i = 0; i < rows.size(); i++) {
            Label cell = new Label(value.apply(rows.get(i)));
            cell.getStyleClass().add("compare-value");
            cell.setWrapText(true);
            cell.setMaxWidth(230);
            grid.add(cell, i + 1, rowIndex);
        }
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
