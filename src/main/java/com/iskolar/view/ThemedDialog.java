package com.iskolar.view;

import com.iskolar.util.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

/**
 * Applies the in-app modal chrome to a JavaFX {@link Dialog}: an undecorated
 * window, the Warm Horizon palette (light or dark), a compact themed header
 * with an eyebrow label and close button, and centering over the owner.
 *
 * <p>JavaFX dialogs otherwise show a native OS title bar (white in dark mode)
 * and duplicate the title text.</p>
 */
public final class ThemedDialog {

    private static double dragX;
    private static double dragY;

    private ThemedDialog() {
    }

    public static void install(Dialog<?> dialog, Window owner, String eyebrow) {
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
        dialog.initStyle(StageStyle.UNDECORATED);

        var pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("app-root", "themed-dialog");
        if (ThemeManager.isDark()) {
            pane.getStyleClass().add("dark");
        }
        pane.setPadding(Insets.EMPTY);

        Node body = pane.getContent();
        VBox wrapper = new VBox(buildHeader(dialog, eyebrow));
        if (body != null) {
            VBox.setVgrow(body, Priority.ALWAYS);
            wrapper.getChildren().add(body);
        }
        pane.setContent(wrapper);

        dialog.setOnShown(e -> centerOverOwner(dialog, owner));
    }

    private static HBox buildHeader(Dialog<?> dialog, String eyebrow) {
        Label label = new Label(eyebrow == null ? "" : eyebrow.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("dialog-eyebrow");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button close = new Button();
        close.setGraphic(FontIcon.of(Feather.X, 16));
        close.getStyleClass().add("dialog-close");
        close.setFocusTraversable(false);
        close.setOnAction(e -> dialog.close());

        HBox header = new HBox(10, label, spacer, close);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setCursor(Cursor.OPEN_HAND);
        header.setOnMousePressed(e -> {
            Window window = dialog.getDialogPane().getScene().getWindow();
            dragX = window.getX() - e.getScreenX();
            dragY = window.getY() - e.getScreenY();
            header.setCursor(Cursor.CLOSED_HAND);
        });
        header.setOnMouseDragged(e -> {
            Window window = dialog.getDialogPane().getScene().getWindow();
            window.setX(e.getScreenX() + dragX);
            window.setY(e.getScreenY() + dragY);
        });
        header.setOnMouseReleased(e -> header.setCursor(Cursor.OPEN_HAND));
        return header;
    }

    private static void centerOverOwner(Dialog<?> dialog, Window owner) {
        if (owner == null) {
            return;
        }
        Window window = dialog.getDialogPane().getScene().getWindow();
        window.setX(Math.max(0, owner.getX() + (owner.getWidth() - window.getWidth()) / 2));
        window.setY(Math.max(0, owner.getY() + (owner.getHeight() - window.getHeight()) / 2));
    }
}
