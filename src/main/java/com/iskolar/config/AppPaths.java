package com.iskolar.config;

import java.nio.file.Path;

/**
 * Resolves the per-user application data directory.
 *
 * <p>Windows: {@code %APPDATA%\Iskolar}
 * <br>Fallback (non-Windows): {@code ~/.iskolar/Iskolar}
 *
 * <p>The SQLite database lives at {@link #dbPath()}.
 */
public final class AppPaths {

    private AppPaths() {
    }

    public static Path dataDir() {
        String appData = System.getenv("APPDATA");
        Path base = (appData != null && !appData.isBlank())
                ? Path.of(appData)
                : Path.of(System.getProperty("user.home"), ".iskolar");
        return base.resolve("Iskolar");
    }

    public static Path dbPath() {
        return dataDir().resolve("iskolar.db");
    }
}
