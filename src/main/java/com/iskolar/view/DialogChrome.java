package com.iskolar.view;

import com.iskolar.util.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

/**
 * Theme-aware chrome for the app's modal dialogs.
 *
 * <p>JavaFX dialogs normally get an operating-system title bar, which stays
 * light even when the app is in dark mode. Installing this chrome makes the
 * dialog undecorated and adds an in-app header (eyebrow label + close button)
 * that follows the active palette, supports dragging, and centers the dialog
 * over its owner window.</p>
 */
public final class DialogChrome {

    private DialogChrome() {
    }

    /**
     * Applies the themed chrome to {@code dialog}. Call after the content and
     * button types have been configured, but before {@code show()}.
     *
     * @param eyebrow short caption shown at the top-left of the header
     */
    public static void install(Dialog<?> dialog, Window owner, String eyebrow) {
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initStyle(StageStyle.UNDECORATED);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("app-root", "themed-dialog");
        if (ThemeManager.isDark()) {
            pane.getStyleClass().add("dark");
        }
        pane.setPadding(Insets.EMPTY);

        Node content = pane.getContent();
        VBox body = new VBox(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        pane.setContent(new VBox(buildHeader(dialog, eyebrow), body));

        dialog.setOnShown(e -> centerOnOwner(dialog, owner));
    }

    private static HBox buildHeader(Dialog<?> dialog, String eyebrow) {
        Label label = new Label(eyebrow.toUpperCase(Locale.ROOT));
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

        double[] grab = new double[2];
        header.setOnMousePressed(e -> {
            Window window = windowOf(dialog);
            if (window == null) {
                return;
            }
            grab[0] = e.getScreenX() - window.getX();
            grab[1] = e.getScreenY() - window.getY();
            header.setCursor(Cursor.CLOSED_HAND);
        });
        header.setOnMouseDragged(e -> {
            Window window = windowOf(dialog);
            if (window == null) {
                return;
            }
            window.setX(e.getScreenX() - grab[0]);
            window.setY(e.getScreenY() - grab[1]);
        });
        header.setOnMouseReleased(e -> header.setCursor(Cursor.OPEN_HAND));

        return header;
    }

    private static Window windowOf(Dialog<?> dialog) {
        return dialog.getDialogPane().getScene() == null
                ? null
                : dialog.getDialogPane().getScene().getWindow();
    }

    private static void centerOnOwner(Dialog<?> dialog, Window owner) {
        Window window = windowOf(dialog);
        if (window == null || owner == null) {
            return;
        }
        double x = owner.getX() + (owner.getWidth() - window.getWidth()) / 2.0;
        double y = owner.getY() + (owner.getHeight() - window.getHeight()) / 2.0;
        window.setX(Math.max(0, Math.round(x)));
        window.setY(Math.max(0, Math.round(y)));
    }
}
