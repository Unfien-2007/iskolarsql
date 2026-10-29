package com.iskolar.view;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * In-window opening splash — "Three-Node Path &amp; Handoff" (~3.1s).
 *
 * <p>Layered on top of the {@link ShellView} inside a {@code StackPane}:
 * a Discover → Match → Track node path draws itself, the brand + tagline
 * rise in, then the overlay fades out (scaling up slightly) while the
 * sidebar slides in and the content rises — all on one sharp ease-out
 * spline. Plays on every launch and can be skipped at any time by
 * clicking anywhere on the overlay.
 */
final class IntroSplash {

    /** Sharp ease-out curve used for every motion in the intro. */
    private static final Interpolator SPLINE = Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0);

    /** Pixel length of the drawn node path (straight line from x=6 to x=234). */
    private static final double LINE_LENGTH = 228;

    /** Node-path x positions for Discover / Match / Track. */
    private static final double[] NODE_X = {6, 120, 234};
    private static final String[] NODE_NAMES = {"Discover", "Match", "Track"};

    private final StackPane host;
    private final StackPane overlay;
    private final Node sidebar;
    private final Node content;
    private final Runnable onFinished;
    private final List<Animation> running = new ArrayList<>();
    private final List<Node> nodes = new ArrayList<>();

    private Label title;
    private Label tagline;
    private Polyline line;
    private boolean finished;

    private IntroSplash(StackPane host, StackPane overlay, Node sidebar, Node content, Runnable onFinished) {
        this.host = host;
        this.overlay = overlay;
        this.sidebar = sidebar;
        this.content = content;
        this.onFinished = onFinished;
    }

    /**
     * Builds the overlay, adds it on top of {@code host} and returns the
     * controller. The caller must already have set the shell's pre-reveal
     * states (sidebar shifted left, content hidden) and then call
     * {@link #play()}.
     */
    static IntroSplash attach(StackPane host, Node sidebar, Node content, Runnable onFinished) {
        IntroSplash splash = new IntroSplash(host, new StackPane(), sidebar, content, onFinished);
        splash.buildOverlay();
        host.getChildren().add(splash.overlay);
        splash.overlay.setOnMouseClicked(e -> splash.skip());
        return splash;
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private void buildOverlay() {
        title = new Label("iskolarPath");
        title.getStyleClass().add("splash-title");
        title.setOpacity(0);
        title.setTranslateY(16);

        Pane nodePath = buildNodePath();

        tagline = new Label("Discover, match, and track your scholarship journey.");
        tagline.getStyleClass().add("splash-tagline");
        tagline.setOpacity(0);
        tagline.setTranslateY(12);

        VBox card = new VBox(18, title, nodePath, tagline);
        card.setAlignment(Pos.CENTER);

        overlay.getStyleClass().add("splash-overlay");
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlay.getChildren().add(card);
    }

    private Pane buildNodePath() {
        Pane pane = new Pane();
        pane.setPrefSize(240, 56);
        pane.setMinSize(240, 56);
        pane.setMaxSize(240, 56);

        line = new Polyline(NODE_X[0], 12, NODE_X[1], 12, NODE_X[2], 12);
        line.getStyleClass().add("splash-line");
        line.getStrokeDashArray().setAll(LINE_LENGTH, LINE_LENGTH);
        line.setStrokeDashOffset(LINE_LENGTH);
        pane.getChildren().add(line);

        for (int i = 0; i < NODE_X.length; i++) {
            VBox node = new VBox(4);
            node.setPrefSize(56, 56);
            node.setAlignment(Pos.TOP_CENTER);

            HBox dotBox = new HBox();
            dotBox.setAlignment(Pos.CENTER);
            dotBox.setPrefHeight(16);
            Circle dot = new Circle(5);
            dot.getStyleClass().add("splash-node");
            dotBox.getChildren().add(dot);

            Label name = new Label(NODE_NAMES[i]);
            name.getStyleClass().add("splash-node-label");
            name.setMaxWidth(Double.MAX_VALUE);
            name.setAlignment(Pos.CENTER);

            node.getChildren().addAll(dotBox, name);
            node.setLayoutX(NODE_X[i] - 28);
            node.setLayoutY(4);
            node.setScaleX(0);
            node.setScaleY(0);
            node.setOpacity(0);
            pane.getChildren().add(node);
            nodes.add(node);
        }
        return pane;
    }

    // ------------------------------------------------------------------
    // Timeline
    // ------------------------------------------------------------------

    /** Timing (ms) — tune these to lengthen or shorten the intro. */
    private static final int LINE_DRAW_MS = 1000;
    private static final int NODE_POP_MS = 350;
    private static final int NODE_FADE_MS = 300;
    private static final int TITLE_DELAY_MS = 500;
    private static final int TITLE_RISE_MS = 700;
    private static final int TAGLINE_DELAY_MS = 950;
    private static final int TAGLINE_RISE_MS = 700;
    private static final int HANDOFF_DELAY_MS = 2300;
    private static final int HANDOFF_MS = 800;

    /** Kicks off the full ~3.1s sequence. */
    void play() {
        Timeline draw = new Timeline(new KeyFrame(Duration.millis(LINE_DRAW_MS),
                new KeyValue(line.strokeDashOffsetProperty(), 0, SPLINE)));
        playAndTrack(draw);

        // Each node pops as the line reaches it (progress ≈ x / length).
        for (int i = 0; i < nodes.size(); i++) {
            double delay = LINE_DRAW_MS * (NODE_X[i] / LINE_LENGTH);
            Node node = nodes.get(i);
            ScaleTransition scale = new ScaleTransition(Duration.millis(NODE_POP_MS), node);
            scale.setToX(1);
            scale.setToY(1);
            scale.setInterpolator(SPLINE);
            FadeTransition fade = new FadeTransition(Duration.millis(NODE_FADE_MS), node);
            fade.setToValue(1);
            playAndTrack(new SequentialTransition(
                    new PauseTransition(Duration.millis(delay)),
                    new ParallelTransition(scale, fade)));
        }

        stagger(title, TITLE_DELAY_MS, TITLE_RISE_MS);
        stagger(tagline, TAGLINE_DELAY_MS, TAGLINE_RISE_MS);

        PauseTransition hold = new PauseTransition(Duration.millis(HANDOFF_DELAY_MS));
        hold.setOnFinished(e -> beginHandoff());
        playAndTrack(hold);
    }

    private void stagger(Node node, double delayMs, double durationMs) {
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setToValue(1);
        fade.setInterpolator(SPLINE);
        Timeline rise = new Timeline(new KeyFrame(Duration.millis(durationMs),
                new KeyValue(node.translateYProperty(), 0, SPLINE)));
        playAndTrack(new SequentialTransition(
                new PauseTransition(Duration.millis(delayMs)),
                new ParallelTransition(fade, rise)));
    }

    private void playAndTrack(Animation animation) {
        running.add(animation);
        animation.play();
    }

    // ------------------------------------------------------------------
    // Handoff + skip
    // ------------------------------------------------------------------

    private void beginHandoff() {
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);

        // Reset the reveal so the handoff owns the fade-in even though the
        // initial view may already have finished its own entrance fade.
        content.setOpacity(0);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(HANDOFF_MS), overlay);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(SPLINE);

        Timeline zoom = new Timeline(new KeyFrame(Duration.millis(HANDOFF_MS),
                new KeyValue(overlay.scaleXProperty(), 1.03, SPLINE),
                new KeyValue(overlay.scaleYProperty(), 1.03, SPLINE)));

        Timeline reveal = new Timeline(new KeyFrame(Duration.millis(HANDOFF_MS),
                new KeyValue(sidebar.translateXProperty(), 0, SPLINE),
                new KeyValue(content.opacityProperty(), 1.0, SPLINE),
                new KeyValue(content.translateYProperty(), 0, SPLINE)));

        ParallelTransition handoff = new ParallelTransition(fadeOut, zoom, reveal);
        handoff.setOnFinished(e -> finish());
        playAndTrack(handoff);
    }

    /** Click-to-skip: stop every running transition and jump straight to the end. */
    void skip() {
        if (finished) {
            return;
        }
        for (Animation animation : running) {
            animation.stop();
        }
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);
        sidebar.setTranslateX(0);
        content.setOpacity(1);
        content.setTranslateY(0);
        host.getChildren().remove(overlay);
        onFinished.run();
    }
}
