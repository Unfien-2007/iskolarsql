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

        // Kick off a background catalog sync once the window is visible.
        com.iskolar.service.ServiceFactory.syncService().syncInBackground();
    }

    @Override
    public void stop() {
        DatabaseConfig.shutdown();
    }
}
