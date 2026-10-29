package com.iskolar.view;

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
 */
public final class Motion {

    /** The app-wide ease-out curve: Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0). */
    public static final Interpolator SPLINE = Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0);

    private Motion() {
    }

    /** Counts a label from 0% up to {@code target} over ~500ms, ending on {@code endText}. */
    public static void countUp(Label label, double target, String endText) {
        if (target <= 0) {
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

    /** Gentle opacity pulse for urgent (RED) deadline pills. */
    public static void pulse(Label pill) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pill.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(700), new KeyValue(pill.opacityProperty(), 0.55)),
                new KeyFrame(Duration.millis(1400), new KeyValue(pill.opacityProperty(), 1.0)));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    /** Card hover lift: -3px on enter, back to 0 on exit. */
    public static void hoverLift(Node node, boolean up) {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(140),
                new KeyValue(node.translateYProperty(), up ? -3 : 0, SPLINE)));
        timeline.play();
    }
}
