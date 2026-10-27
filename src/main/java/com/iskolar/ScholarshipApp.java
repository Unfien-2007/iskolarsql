package com.iskolar;

import com.iskolar.config.DatabaseConfig;
import com.iskolar.util.ThemeManager;
import com.iskolar.view.ShellView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * The JavaFX application: one primary {@code Stage} with a {@code BorderPane}
 * root — collapsible sidebar rail on the left, dynamic center content swapped
 * by {@code ViewNavigator}. No pop-up window spam.
 */
public class ScholarshipApp extends Application {

    @Override
    public void start(Stage stage) {
        ThemeManager.applySavedTheme();

        BorderPane shell = ShellView.build();
        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().add(Objects.requireNonNull(
                ScholarshipApp.class.getResource("/css/app.css"),
                "css/app.css missing from classpath").toExternalForm());

        stage.setTitle("Iskolar — Scholarship Finder & Application Tracker");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        DatabaseConfig.shutdown();
    }
}
