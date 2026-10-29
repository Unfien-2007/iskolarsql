package com.iskolar.util;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;

/**
 * Bundled typography — Poppins for display/headings and Inter for body/UI.
 * Registered once at startup and referenced from app.css by family name.
 * Both families are distributed under the SIL Open Font License
 * (see {@code /fonts} in resources).
 */
public final class Fonts {

    private static final String[] PATHS = {
            "/fonts/Poppins-Regular.ttf", "/fonts/Poppins-Medium.ttf",
            "/fonts/Poppins-SemiBold.ttf", "/fonts/Poppins-Bold.ttf",
            "/fonts/Inter-Regular.ttf", "/fonts/Inter-Medium.ttf",
            "/fonts/Inter-SemiBold.ttf", "/fonts/Inter-Bold.ttf",
    };

    private Fonts() {
    }

    /** Registers the bundled families with JavaFX (idempotent, best-effort). */
    public static void load() {
        for (String path : PATHS) {
            try (InputStream in = Fonts.class.getResourceAsStream(path)) {
                if (in == null) {
                    System.err.println("[iskolarPath] missing font resource: " + path);
                    continue;
                }
                if (Font.loadFont(in, 14) == null) {
                    System.err.println("[iskolarPath] failed to register font: " + path);
                }
            } catch (IOException e) {
                System.err.println("[iskolarPath] error loading font " + path + ": " + e.getMessage());
            }
        }
    }
}
