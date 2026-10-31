package com.iskolar;

import com.iskolar.config.DatabaseConfig;
import com.iskolar.service.AuthService;
import com.iskolar.service.ServiceFactory;
import com.iskolar.sync.TokenStore;
import com.iskolar.util.Fonts;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ThemeManager;
import com.iskolar.util.ViewNavigator;
import com.iskolar.view.ShellView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Objects;

/**
 * The JavaFX application: one primary {@code Stage} whose root comes from
 * {@code ShellView} — a collapsible sidebar rail on the left, dynamic center
 * content swapped by {@code ViewNavigator}, and (on the very first launch)
 * an in-window intro splash overlay. No pop-up window spam.
 */
public class ScholarshipApp extends Application {

    @Override
    public void start(Stage stage) {
        Fonts.load();
        ThemeManager.applySavedTheme();

        Parent shell = ShellView.build();
        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().add(Objects.requireNonNull(
                ScholarshipApp.class.getResource("/css/app.css"),
                "css/app.css missing from classpath").toExternalForm());

        stage.setTitle("iskolarPath: Discover, match, and track your scholarship journey.");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();

        // Kick off a background catalog sync once the window is visible. After
        // every successful sync the cached sign-in is re-validated against the
        // server — accounts disabled centrally get signed out locally.
        var syncService = ServiceFactory.syncService();
        syncService.addListener(result -> {
            if (result.success()) {
                revalidateSession();
            }
        });
        syncService.syncInBackground();

        // Signed-in users re-validate every five minutes while the app stays
        // open. This keeps them marked online in the admin portal and revokes
        // disabled accounts shortly after they are disabled centrally.
        Timeline heartbeat = new Timeline(
                new KeyFrame(Duration.minutes(5), e -> revalidateSession()));
        heartbeat.setCycleCount(Timeline.INDEFINITE);
        heartbeat.play();
    }

    /** Re-checks the cached token off the FX thread; signs out revoked accounts. */
    private static void revalidateSession() {
        if (SessionManager.current().isEmpty() || TokenStore.token().isEmpty()) {
            return;
        }
        Thread worker = new Thread(() -> {
            AuthService.Revalidation outcome = ServiceFactory.authService().revalidateOnReconnect();
            if (outcome == AuthService.Revalidation.SIGNED_OUT) {
                Platform.runLater(() -> {
                    ShellView.refreshSessionUi();
                    ViewNavigator.show(ViewNavigator.DISCOVERY_VIEW);
                });
            }
        }, "iskolar-auth-revalidate");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        DatabaseConfig.shutdown();
    }
}
