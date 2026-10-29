package com.iskolar.view;

import com.iskolar.util.ViewNavigator;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Path" journey rail: a slim Discover → Match → Track → Award strip at
 * the top of the shell that highlights the current stage of the scholarship
 * journey and echoes the intro splash's node path. The Award step lights up
 * gold once the student has at least one AWARDED application.
 */
public final class JourneyRail {

    private static final String[] STEP_NAMES = {"Discover", "Match", "Track", "Award"};
    private static final Feather[] STEP_ICONS = {
            Feather.COMPASS, Feather.TARGET, Feather.TRENDING_UP, Feather.AWARD};
    /** ShellView nav-button index for each step (Award jumps to the tracker). */
    private static final int[] STEP_NAV = {0, 3, 1, 1};

    private static HBox rail;
    private static final List<HBox> steps = new ArrayList<>();

    private JourneyRail() {
    }

    /** Builds the rail once (called by the shell during startup). */
    public static HBox build() {
        rail = new HBox(2);
        rail.getStyleClass().add("journey-rail");
        rail.setAlignment(Pos.CENTER_LEFT);

        for (int i = 0; i < STEP_NAMES.length; i++) {
            FontIcon icon = FontIcon.of(STEP_ICONS[i], 13);
            icon.getStyleClass().add("rail-icon");
            Label label = new Label(STEP_NAMES[i]);
            label.getStyleClass().add("rail-label");
            HBox step = new HBox(6, icon, label);
            step.getStyleClass().add("rail-step");
            step.setAlignment(Pos.CENTER_LEFT);
            final int navIndex = STEP_NAV[i];
            step.setOnMouseClicked(e -> ShellView.fireNav(navIndex));
            steps.add(step);
            rail.getChildren().add(step);

            if (i < STEP_NAMES.length - 1) {
                Region line = new Region();
                line.getStyleClass().add("rail-line");
                rail.getChildren().add(line);
            }
        }
        return rail;
    }

    /**
     * Highlights the step for the view being shown, hiding the rail on
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
            HBox step = steps.get(i);
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
        HBox awardStep = steps.get(STEP_NAMES.length - 1);
        if (isAwarded) {
            awardStep.getStyleClass().add("awarded");
        } else {
            awardStep.getStyleClass().remove("awarded");
        }
    }
}
