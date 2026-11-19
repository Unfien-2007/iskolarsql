package com.iskolar;

import com.iskolar.config.DatabaseConfig;
import com.iskolar.dao.DaoFactory;
import com.iskolar.service.AuthService;
import com.iskolar.service.ServiceFactory;
import com.iskolar.sync.TokenStore;
import com.iskolar.util.Fonts;
import com.iskolar.util.Prefs;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ThemeManager;
import com.iskolar.util.ViewNavigator;
import com.iskolar.view.ShellView;
import com.iskolar.view.WindowChrome;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Objects;

/**
 * The JavaFX application: one primary {@code Stage} whose root comes from
 * {@code ShellView} — a collapsible sidebar rail on the left, dynamic center
 * content swapped by {@code ViewNavigator}, and an in-window intro splash
 * overlay on every launch. No pop-up window spam.
 */
public class ScholarshipApp extends Application {

    @Override
    public void start(Stage stage) {
        Fonts.load();
        ThemeManager.applySavedTheme();
        // The app draws its own caption, so the stage has no native title bar.
        stage.initStyle(StageStyle.UNDECORATED);
        WindowChrome.install(stage);

        Parent shell = ShellView.build();
        Scene scene = new Scene(shell, initialWidth(), initialHeight());
        scene.getStylesheets().add(Objects.requireNonNull(
                ScholarshipApp.class.getResource("/css/app.css"),
                "css/app.css missing from classpath").toExternalForm());
        installShortcuts(scene);
        WindowChrome.attach(scene);

        // Taskbar/Alt-Tab label; nothing is drawn in the window itself.
        stage.setTitle("iskolarPath");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        if (Prefs.windowMaximized()) {
            stage.setMaximized(true);
        }
        // Remember the window size between sessions; skip the write when the
        // user closes while maximized so the restored size stays the small one.
        stage.setOnCloseRequest(e -> {
            Prefs.windowMaximized(stage.isMaximized());
            if (!stage.isMaximized()) {
                Prefs.windowSize(stage.getWidth(), stage.getHeight());
            }
        });
        stage.show();
        // Start the intro only now: the timeline is wall-clock based, so
        // playing it before the first paint would skip its opening draw.
        ShellView.playIntro();

        // A scholarship whose application period has closed should stop
        // appearing as active. Flip expired-but-active rows to inactive on
        // launch (and again after each successful sync) so the catalog stays
        // accurate even without a server round-trip.
        deactivateExpiredScholarships();

        // Kick off a background catalog sync once the window is visible. After
        // every successful sync the cached sign-in is re-validated against the
        // server — accounts disabled centrally get signed out locally.
        var syncService = ServiceFactory.syncService();
        syncService.addListener(result -> {
            if (result.success()) {
                deactivateExpiredScholarships();
                revalidateSession();
            }
        });
        syncService.syncInBackground();
        // Render's free tier sleeps idle services; ask it to wake up while the
        // user is still typing, so sign-in and registration feel responsive.
        syncService.warmUpInBackground();

        // Signed-in users re-validate every five minutes while the app stays
        // open. This keeps them marked online in the admin portal and revokes
        // disabled accounts shortly after they are disabled centrally.
        Timeline heartbeat = new Timeline(
                new KeyFrame(Duration.minutes(5), e -> revalidateSession()));
        heartbeat.setCycleCount(Timeline.INDEFINITE);
        heartbeat.play();
    }

    /** Restored window size, clamped to the app minimum and this screen's width. */
    private static double initialWidth() {
        double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
        return Math.min(Math.max(960, Prefs.windowWidth()), Math.max(960, screenWidth));
    }

    private static double initialHeight() {
        double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();
        return Math.min(Math.max(640, Prefs.windowHeight()), Math.max(640, screenHeight));
    }

    /**
     * App-wide keyboard shortcuts: Ctrl+F jumps to the Discovery search box and
     * Ctrl+1…4 switch destinations, mirroring the sidebar order.
     */
    private static void installShortcuts(Scene scene) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                () -> {
                    Node search = scene.getRoot().lookup("#searchField");
                    if (search instanceof TextField field) {
                        field.requestFocus();
                        field.selectAll();
                    }
                });
        KeyCode[] digits = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4};
        for (int i = 0; i < digits.length; i++) {
            final int navIndex = i;
            scene.getAccelerators().put(
                    new KeyCodeCombination(digits[i], KeyCombination.SHORTCUT_DOWN),
                    () -> ShellView.fireNav(navIndex));
        }
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

    /**
     * Runs {@link com.iskolar.dao.ScholarshipDao#deactivateExpired()} as a
     * best-effort housekeeping step. A failure here must never block startup
     * or a sync notification, so it is swallowed.
     */
    private static void deactivateExpiredScholarships() {
        try {
            DaoFactory.scholarshipDao().deactivateExpired();
        } catch (RuntimeException ignored) {
            // Housekeeping only; the discovery query still filters expired rows.
        }
    }

    @Override
    public void stop() {
        DatabaseConfig.shutdown();
    }
}
