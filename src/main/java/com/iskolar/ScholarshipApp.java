package com.iskolar;

import com.iskolar.config.DatabaseConfig;
import com.iskolar.util.Fonts;
import com.iskolar.util.ThemeManager;
import com.iskolar.view.ShellView;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
        var syncService = com.iskolar.service.ServiceFactory.syncService();
        syncService.addListener(result -> {
            if (result.success()) {
                Thread worker = new Thread(() -> {
                    var outcome = com.iskolar.service.ServiceFactory.authService()
                            .revalidateOnReconnect();
                    if (outcome == com.iskolar.service.AuthService.Revalidation.SIGNED_OUT) {
                        javafx.application.Platform.runLater(() -> {
                            ShellView.refreshSessionUi();
                            com.iskolar.util.ViewNavigator.show(
                                    com.iskolar.util.ViewNavigator.DISCOVERY_VIEW);
                        });
                    }
                }, "iskolar-auth-revalidate");
                worker.setDaemon(true);
                worker.start();
            }
        });
        syncService.syncInBackground();
    }

    @Override
    public void stop() {
        DatabaseConfig.shutdown();
    }
}
