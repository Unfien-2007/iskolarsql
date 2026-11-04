package com.iskolar.view;

import com.iskolar.util.ViewNavigator;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Bottom-centre status bar with one action slot ("Undo"), used to confirm
 * destructive tracker actions without a modal dialog.
 *
 * <p>Lives on top of the center content area ({@link ViewNavigator#centerHost()})
 * so it never covers the sidebar, auto-dismisses after a few seconds, and is
 * torn down by navigation like the details/compare overlays.</p>
 */
public final class Snackbar {

    /** How long the bar stays up before dismissing itself. */
    private static final Duration VISIBLE_FOR = Duration.seconds(6);
    private static final Duration FADE = Duration.millis(160);
    /** Keeps the bar from stretching across the whole viewport. */
    private static final double MAX_WIDTH = 560;

    private static StackPane host;
    private static HBox bar;
    private static PauseTransition timer;

    private Snackbar() {
    }

    /**
     * Shows (or replaces) the snackbar.
     *
     * @param message    short confirmation text, e.g. "Removed ₱5,000 GWA grant"
     * @param actionText label for the action button, or {@code null} for none
     * @param action     run when the action is pressed (the bar always closes first)
     */
    public static void show(String message, String actionText, Runnable action) {
        StackPane target = ViewNavigator.centerHost();
        if (target == null || message == null) {
            return;
        }
        dismiss();

        Label text = new Label(message);
        text.getStyleClass().add("snackbar-message");
        text.setWrapText(true);
        text.setMaxWidth(MAX_WIDTH - 160);

        bar = new HBox(14, text);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("snackbar");
        bar.setMaxWidth(MAX_WIDTH);
        bar.setMaxHeight(Region.USE_PREF_SIZE);

        if (actionText != null && action != null) {
            Button button = new Button(actionText);
            button.getStyleClass().add("snackbar-action");
            button.setOnAction(e -> {
                dismiss();
                action.run();
            });
            bar.getChildren().add(button);
        }

        host = target;
        StackPane.setAlignment(bar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(bar, new Insets(0, 0, 24, 0));
        target.getChildren().add(bar);

        if (Motion.reduced()) {
            scheduleDismiss();
            return;
        }
        FadeTransition fadeIn = new FadeTransition(FADE, bar);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        TranslateTransition rise = new TranslateTransition(FADE, bar);
        rise.setFromY(12);
        rise.setToY(0);
        ParallelTransition entrance = new ParallelTransition(fadeIn, rise);
        entrance.setOnFinished(e -> scheduleDismiss());
        entrance.play();
    }

    private static void scheduleDismiss() {
        if (bar == null) {
            return;
        }
        timer = new PauseTransition(VISIBLE_FOR);
        timer.setOnFinished(e -> dismiss());
        timer.play();
    }

    /** Removes the bar immediately (safe to call when nothing is showing). */
    public static void dismiss() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (bar != null && host != null) {
            host.getChildren().remove(bar);
        }
        bar = null;
        host = null;
    }
}