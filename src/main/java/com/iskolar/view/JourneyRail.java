package com.iskolar.view;

import com.iskolar.util.ViewNavigator;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * The centred "Path" stepper: numbered Discover → Match → Track → Award steps
 * joined by hairlines, highlighting the current stage of the journey.
 *
 * <p>The Award step lights up once the student has at least one AWARDED
 * application. Each step is a real {@code Button}, so the strip is tabbable
 * and Enter/Space navigates like the sidebar rail it mirrors.</p>
 */
public final class JourneyRail {

    private static final String[] STEP_NAMES = {"Discover", "Match", "Track", "Award"};
    /** ShellView nav-button index for each step (Award jumps to the tracker). */
    private static final int[] STEP_NAV = {0, 2, 1, 1};

    private static HBox rail;
    private static final List<Button> steps = new ArrayList<>();

    private JourneyRail() {
    }

    /** Builds the stepper once (called by the shell during startup). */
    public static HBox build() {
        rail = new HBox(8);
        rail.getStyleClass().add("journey-rail");
        rail.setAlignment(Pos.CENTER);

        for (int i = 0; i < STEP_NAMES.length; i++) {
            Label number = new Label(String.valueOf(i + 1));
            number.getStyleClass().add("step-number");
            Label label = new Label(STEP_NAMES[i]);
            label.getStyleClass().add("step-label");

            HBox content = new HBox(8, number, label);
            content.setAlignment(Pos.CENTER_LEFT);

            final int navIndex = STEP_NAV[i];
            Button step = new Button();
            step.setGraphic(content);
            step.getStyleClass().add("step");
            step.setAccessibleText("Go to " + STEP_NAMES[i]);
            step.setOnAction(e -> ShellView.fireNav(navIndex));
            steps.add(step);
            rail.getChildren().add(step);

            if (i < STEP_NAMES.length - 1) {
                Region line = new Region();
                line.getStyleClass().add("step-line");
                rail.getChildren().add(line);
            }
        }
        return rail;
    }

    /**
     * Highlights the step for the view being shown, hiding the stepper on
     * auth and admin screens (those aren't part of the student journey).
     */
    public static void select(String viewPath) {
        if (rail == null) {
            return;
        }
        boolean hidden = viewPath == null
                || viewPath.equals(ViewNavigator.LOGIN_VIEW)
                || viewPath.equals(ViewNavigator.REGISTER_VIEW)
                || viewPath.equals(ViewNavigator.ADMIN_DASHBOARD_VIEW)
                || viewPath.equals(ViewNavigator.ADMIN_FORM_VIEW);
        rail.setVisible(!hidden);
        rail.setManaged(!hidden);
        if (hidden) {
            return;
        }

        int active = switch (viewPath) {
            case ViewNavigator.DISCOVERY_VIEW -> 0;
            case ViewNavigator.WIZARD_VIEW -> 1;
            case ViewNavigator.TRACKER_VIEW -> 2;
            default -> -1;
        };
        for (int i = 0; i < steps.size(); i++) {
            Button step = steps.get(i);
            step.getStyleClass().remove("active");
            if (i == active) {
                step.getStyleClass().add("active");
            }
        }
    }

    /** Lights the Award step gold (or resets it) — fed by the tracker board. */
    public static void setAwarded(boolean isAwarded) {
        if (rail == null || steps.size() < STEP_NAMES.length) {
            return;
        }
        Button awardStep = steps.get(STEP_NAMES.length - 1);
        if (isAwarded) {
            awardStep.getStyleClass().add("awarded");
        } else {
            awardStep.getStyleClass().remove("awarded");
        }
    }
}
