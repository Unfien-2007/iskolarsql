package com.iskolar.util;

import com.iskolar.model.ThemePref;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Parent;

/**
 * One-click Light/Dark mode switching between the AtlantaFX
 * {@code PrimerLight} and {@code PrimerDark} themes, persisted to
 * {@code Preferences} and re-applied on the next launch. The active theme
 * is also mirrored onto the registered shell root as a {@code dark} style
 * class so app.css can scope theme overrides (the Warm Horizon palette).
 */
public final class ThemeManager {

    private static final ThemePref DEFAULT_THEME = ThemePref.LIGHT;
    private static ThemePref current;
    private static Parent root;

    private ThemeManager() {
    }

    /** Restores the persisted theme (call once, before the Stage is shown). */
    public static void applySavedTheme() {
        apply(Prefs.theme().orElse(DEFAULT_THEME));
    }

    /** Applies and persists the theme (must run on the FX thread). */
    public static void apply(ThemePref theme) {
        current = theme;
        Application.setUserAgentStylesheet(theme == ThemePref.DARK
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        Prefs.theme(theme);
        syncRootClass();
    }

    public static ThemePref current() {
        return current == null ? DEFAULT_THEME : current;
    }

    public static boolean isDark() {
        return current() == ThemePref.DARK;
    }

    public static void toggle() {
        apply(current() == ThemePref.DARK ? ThemePref.LIGHT : ThemePref.DARK);
    }

    /**
     * Registers the shell root so the current theme can be reflected as a
     * {@code dark} style class (used by app.css to scope dark overrides).
     */
    public static void registerRoot(Parent appRoot) {
        root = appRoot;
        syncRootClass();
    }

    private static void syncRootClass() {
        if (root == null) {
            return;
        }
        if (isDark()) {
            if (!root.getStyleClass().contains("dark")) {
                root.getStyleClass().add("dark");
            }
        } else {
            root.getStyleClass().remove("dark");
        }
    }
}
