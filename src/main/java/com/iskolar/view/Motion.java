package com.iskolar.view;

import com.iskolar.util.Prefs;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Shared micro-motion helpers so the whole app moves on the same sharp
 * ease-out curve as the intro splash ("Warm Horizon" motion language).
 *
 * <p>Every helper short-circuits when {@link #reduced()} is set (system
 * property {@code -Diskolar.reduceMotion=true} or the {@code ui.reduceMotion}
 * preference), so users who get motion sickness get the end state immediately.</p>
 */
public final class Motion {

    /** The app-wide ease-out curve: Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0). */
    public static final Interpolator SPLINE = Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0);

    /**
     * How many times an urgent pill pulses before settling. The pulse used to
     * run {@link Animation#INDEFINITE}, which kept one timeline + pulse per card
     * alive for as long as the grid was on screen.
     */
    private static final int PULSE_CYCLES = 3;

    private static final boolean REDUCED =
            Boolean.getBoolean("iskolar.reduceMotion") || Prefs.reduceMotion();

    private Motion() {
    }

    /** {@code true} when animation should be reduced to instant end states. */
    public static boolean reduced() {
        return REDUCED;
    }

    /** Counts a label from 0% up to {@code target} over ~500ms, ending on {@code endText}. */
    public static void countUp(Label label, double target, String endText) {
        if (REDUCED || target <= 0) {
            label.setText(endText);
            return;
        }
        int steps = 20;
        Timeline timeline = new Timeline();
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(25.0 * step),
                    e -> label.setText(Math.round(target * step / steps) + "%")));
        }
        timeline.setOnFinished(e -> label.setText(endText));
        timeline.play();
    }

    /** Gentle opacity pulse for urgent (RED) deadline pills; settles after a few cycles. */
    public static void pulse(Label pill) {
        if (REDUCED) {
            pill.setOpacity(1.0);
            return;
        }
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pill.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(700), new KeyValue(pill.opacityProperty(), 0.55)),
                new KeyFrame(Duration.millis(1400), new KeyValue(pill.opacityProperty(), 1.0)));
        timeline.setCycleCount(PULSE_CYCLES);
        // Never leave a stopped pill stranded at 0.55 opacity.
        timeline.setOnFinished(e -> pill.setOpacity(1.0));
        timeline.play();
    }

    /** Card hover lift: -3px on enter, back to 0 on exit. */
    public static void hoverLift(Node node, boolean up) {
        if (REDUCED) {
            node.setTranslateY(0);
            return;
        }
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(140),
                new KeyValue(node.translateYProperty(), up ? -3 : 0, SPLINE)));
        timeline.play();
    }
}
