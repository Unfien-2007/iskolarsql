package com.iskolar.util;

import com.iskolar.model.ThemePref;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;

/**
 * One-click Light/Dark mode switching between the AtlantaFX
 * {@code PrimerLight} and {@code PrimerDark} themes, persisted to
 * {@code Preferences} and re-applied on the next launch.
 */
public final class ThemeManager {

    private static final ThemePref DEFAULT_THEME = ThemePref.LIGHT;
    private static ThemePref current;

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
    }

    public static ThemePref current() {
        return current == null ? DEFAULT_THEME : current;
    }

    public static void toggle() {
        apply(current() == ThemePref.DARK ? ThemePref.LIGHT : ThemePref.DARK);
    }
}
