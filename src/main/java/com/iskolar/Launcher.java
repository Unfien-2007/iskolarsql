package com.iskolar;

import javafx.application.Application;

/**
 * Application entry point.
 *
 * <p>Kept outside the JavaFX {@code Application} subclass so
 * {@code mvn javafx:run} works without a module descriptor.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(ScholarshipApp.class, args);
    }
}
