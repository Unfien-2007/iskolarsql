package com.iskolar.view;

import com.iskolar.dto.ComparisonRow;
import com.iskolar.util.GwaConverter;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Function;

/**
 * In-app side-by-side comparison overlay: up to 3 scholarships against
 * tuition coverage, monthly stipend, maintaining GWA, and return-of-service.
 *
 * <p>Matches the details drawer's look (themed header + ✕) but opens centered
 * with a dimmed scrim, since a comparison needs the full width. Close with the
 * header ✕, {@code Esc}, or by clicking anywhere outside the panel; navigation
 * closes it automatically.</p>
 */
public final class ComparisonPanel {

    private static StackPane layer;
    private static VBox panel;
    private static EventHandler<KeyEvent> escHandler;
    private static Scene scene;

    private ComparisonPanel() {
    }

    /** Requires at least 2 rows to be meaningful. */
    public static void show(StackPane host, List<ComparisonRow> rows) {
        if (host == null || rows == null || rows.size() < 2) {
            return;
        }
        dismiss();

        Region scrim = new Region();
        scrim.getStyleClass().add("compare-scrim");
        scrim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        scrim.setOnMousePressed(e -> close());

        panel = new VBox(buildHeader(), buildBody(rows));
        panel.getStyleClass().add("compare-panel");
        double width = Math.min(260 + rows.size() * 260, 900);
        panel.setPrefWidth(width);
        panel.setMaxWidth(width);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.setMinHeight(0);
        panel.setOpacity(0);
        panel.setScaleX(0.98);
        panel.setScaleY(0.98);

        layer = new StackPane(scrim, panel);
        layer.getStyleClass().add("compare-overlay");
        layer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(panel, Pos.CENTER);
        host.getChildren().add(layer);

        Scene target = host.getScene();
        if (target != null) {
            scene = target;
            escHandler = e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    close();
                    e.consume();
                }
            };
            scene.addEventFilter(KeyEvent.KEY_PRESSED, escHandler);
        }

        FadeTransition fade = new FadeTransition(Duration.millis(160), panel);
        fade.setToValue(1);
        fade.setInterpolator(Motion.SPLINE);
        ScaleTransition scale = new ScaleTransition(Duration.millis(160), panel);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Motion.SPLINE);
        new ParallelTransition(fade, scale).play();
    }

    /** Removes the overlay immediately (used on navigation). */
    public static void dismiss() {
        if (escHandler != null && scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, escHandler);
        }
        escHandler = null;
        scene = null;
        if (layer != null && layer.getParent() instanceof Pane parent) {
            parent.getChildren().remove(layer);
        }
        layer = null;
        panel = null;
    }

    private static void close() {
        if (layer == null || panel == null) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(140), panel);
        fade.setToValue(0);
        fade.setInterpolator(Motion.SPLINE);
        ScaleTransition scale = new ScaleTransition(Duration.millis(140), panel);
        scale.setToX(0.98);
        scale.setToY(0.98);
        scale.setInterpolator(Motion.SPLINE);
        ParallelTransition out = new ParallelTransition(fade, scale);
        out.setOnFinished(e -> dismiss());
        out.play();
    }

    // ------------------------------------------------------------------
    // Chrome + content
    // ------------------------------------------------------------------

    private static HBox buildHeader() {
        Label eyebrow = new Label("COMPARE SCHOLARSHIPS");
        eyebrow.getStyleClass().add("details-drawer-eyebrow");
        Region spacer = new Region();
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

    private static ScrollPane buildBody(List<ComparisonRow> rows) {
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

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("details-scroll");
        scroll.setMinHeight(0);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
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
