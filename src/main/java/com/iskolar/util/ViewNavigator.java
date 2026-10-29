package com.iskolar.util;

import com.iskolar.view.ComparisonPanel;
import com.iskolar.view.JourneyRail;
import com.iskolar.view.ScholarshipDetailsPanel;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Single-window navigation: swaps the center content of the shell's
 * {@code BorderPane}. Views are re-loaded per navigation (FXML is cheap and
 * state always reflects fresh data from the DAOs).
 */
public final class ViewNavigator {

    public static final String LOGIN_VIEW = "/view/login.fxml";
    public static final String REGISTER_VIEW = "/view/register.fxml";
    public static final String DISCOVERY_VIEW = "/view/discovery.fxml";
    public static final String WIZARD_VIEW = "/view/wizard.fxml";
    public static final String TRACKER_VIEW = "/view/tracker.fxml";
    public static final String ADMIN_DASHBOARD_VIEW = "/view/admin_dashboard.fxml";
    public static final String ADMIN_FORM_VIEW = "/view/admin_form.fxml";

    private static BorderPane root;
    private static StackPane centerHost;

    private ViewNavigator() {
    }

    /** Must be called once by the shell before any navigation. */
    public static void init(BorderPane root) {
        ViewNavigator.root = root;
    }

    /**
     * The current center content area. In-app overlays (such as the scholarship
     * details drawer) attach to this {@code StackPane}.
     */
    public static StackPane centerHost() {
        return centerHost;
    }

    public static void show(String fxmlPath) {
        show(fxmlPath, null);
    }

    /**
     * Loads an FXML view into the center area.
     *
     * @param controllerInit optional initializer receiving the fresh controller
     *                       (e.g. to pass a selected scholarship id)
     */
    public static void show(String fxmlPath, Consumer<Object> controllerInit) {
        BorderPane target = root;
        if (target == null) {
            throw new IllegalStateException("ViewNavigator has not been initialised");
        }
        try {
            FXMLLoader loader = new FXMLLoader(ViewNavigator.class.getResource(fxmlPath));
            Parent view = loader.load();
            if (controllerInit != null) {
                controllerInit.accept(loader.getController());
            }
            ScholarshipDetailsPanel.dismiss();
            ComparisonPanel.dismiss();
            StackPane host = new StackPane(view);
            centerHost = host;
            target.setCenter(host);
            JourneyRail.select(fxmlPath);
            fadeIn(host);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load view: " + fxmlPath, e);
        }
    }

    /** Shows an already-built view (used by code-built screens). */
    public static void show(Parent view) {
        if (root == null) {
            throw new IllegalStateException("ViewNavigator has not been initialised");
        }
        ScholarshipDetailsPanel.dismiss();
        ComparisonPanel.dismiss();
        StackPane host = new StackPane(view);
        centerHost = host;
        root.setCenter(host);
        JourneyRail.select(null);
        fadeIn(host);
    }

    /** Soft cross-fade so view switches never hard-cut. */
    private static void fadeIn(Parent view) {
        view.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(160), view);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0));
        fade.play();
    }
}
