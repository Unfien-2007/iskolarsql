package com.iskolar.view;

import com.iskolar.controller.DiscoveryController;
import com.iskolar.dao.DaoFactory;
import com.iskolar.model.Role;
import com.iskolar.model.ThemePref;
import com.iskolar.util.Prefs;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ThemeManager;
import com.iskolar.util.ViewNavigator;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single-window shell: a collapsible left navigation rail (brand, nav
 * items, session chip, theme toggle, login/logout) around a dynamic center
 * area managed by {@link ViewNavigator}.
 */
public final class ShellView {

    public static final double SIDEBAR_EXPANDED = 220;
    public static final double SIDEBAR_COLLAPSED = 68;

    private static VBox sidebar;
    private static Label brandLabel;
    private static Label nameLabel;
    private static Label roleLabel;
    private static Button authButton;
    private static Button themeButton;
    private static Button logoutButton;
    private static Button collapseButton;
    private static HBox brandRow;
    private static HBox brandMark;
    private static Region brandSpacer;
    private static ToggleButton adminButton;
    /** Destinations in rail order: Discover, My Applications, My Profile, Admin. */
    private static final List<ToggleButton> NAV_BUTTONS = new ArrayList<>();
    private static final Map<Labeled, String> NAV_TITLES = new HashMap<>();
    private static final Map<ToggleButton, String> NAV_VIEWS = new HashMap<>();
    private static final Map<Node, Tooltip> TOOLTIPS = new HashMap<>();
    private static HBox userChip;
    private static boolean collapsed;

    private ShellView() {
    }

    /**
     * Builds the shell. The first launch on a machine plays the in-window intro
     * splash and hands off into the shell (see {@link IntroSplash}); later
     * launches open straight into Discover. Pass
     * {@code -Diskolar.skipIntro=true} to bypass it during development, or
     * {@code -Diskolar.forceIntro=true} to replay it.
     */
    public static Parent build() {
        BorderPane root = new BorderPane();
        ViewNavigator.init(root);
        root.setLeft(buildSidebar());
        root.setTop(buildTopArea());
        refreshSessionUi();
        NAV_BUTTONS.get(0).fire(); // land on Discover (loads while the stage is still hidden)

        if (!shouldPlayIntro()) {
            root.getStyleClass().add("app-root");
            ThemeManager.registerRoot(root);
            return root;
        }
        return attachIntro(root);
    }

    /**
     * Flip to {@code true} (or pass {@code -Diskolar.skipIntro=true}) to
     * bypass the splash during development.
     */
    private static final boolean SKIP_INTRO_ANIMATION = Boolean.getBoolean("iskolar.skipIntro");

    /** Replay the splash on a machine that has already seen it ({@code -Diskolar.forceIntro=true}). */
    private static final boolean FORCE_INTRO = Boolean.getBoolean("iskolar.forceIntro");

    /**
     * The splash is a first-run welcome, not a launch tax: it plays once per
     * machine and is remembered in {@link Prefs}.
     */
    private static boolean shouldPlayIntro() {
        return !SKIP_INTRO_ANIMATION && (FORCE_INTRO || !Prefs.introPlayed());
    }

    /**
     * Layers the intro splash over the shell inside a {@code StackPane} and
     * prepares the sidebar + center content for the reveal handoff.
     */
    private static Parent attachIntro(BorderPane shell) {
        StackPane stack = new StackPane(shell);
        stack.getStyleClass().add("app-root");
        ThemeManager.registerRoot(stack);

        Node content = shell.getCenter();
        if (content == null) {
            content = new Region();
            shell.setCenter(content);
        }

        sidebar.setTranslateX(-SIDEBAR_EXPANDED);
        content.setOpacity(0);
        content.setTranslateY(12);

        Prefs.introPlayed(true); // first-run only from here on
        IntroSplash splash = IntroSplash.attach(stack, sidebar, content, () -> { });
        splash.play();
        return stack;
    }

    /** Top area: the Path journey rail. */
    private static HBox buildTopArea() {
        return JourneyRail.build();
    }

    /** Fires a sidebar nav button by index (used by the journey rail + shortcuts). */
    public static void fireNav(int index) {
        if (index >= 0 && index < NAV_BUTTONS.size()) {
            ToggleButton button = NAV_BUTTONS.get(index);
            if (button.isVisible()) {
                button.fire();
            }
        }
    }

    /**
     * Mirrors the destination now on screen in the rail, so navigation from the
     * journey rail, a gate button, or a snackbar never leaves the wrong item
     * highlighted. Called by {@link ViewNavigator} after every swap.
     */
    public static void syncSelection(String viewPath) {
        for (ToggleButton button : NAV_BUTTONS) {
            String path = NAV_VIEWS.get(button);
            boolean current = path != null && path.equals(viewPath);
            // The scholarship form is reached through the Admin Portal entry.
            if (!current && ViewNavigator.ADMIN_DASHBOARD_VIEW.equals(path)
                    && ViewNavigator.ADMIN_FORM_VIEW.equals(viewPath)) {
                current = true;
            }
            button.setSelected(current);
        }
    }

    // ------------------------------------------------------------------
    // Sidebar construction
    // ------------------------------------------------------------------

    private static VBox buildSidebar() {
        sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(SIDEBAR_EXPANDED);
        sidebar.setMinWidth(SIDEBAR_COLLAPSED);
        sidebar.setMaxWidth(SIDEBAR_EXPANDED);

        // brand + collapse toggle
        brandMark = new HBox(10, FontIcon.of(Feather.BOOK_OPEN, 20), brandLabel = new Label("iskolarPath"));
        brandMark.setAlignment(Pos.CENTER_LEFT);
        brandLabel.getStyleClass().add("brand-title");
        brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);
        collapseButton = new Button("", FontIcon.of(Feather.MENU, 18));
        collapseButton.getStyleClass().add("icon-button");
        collapseButton.setTooltip(new Tooltip("Collapse sidebar"));
        collapseButton.setAccessibleText("Collapse sidebar");
        collapseButton.setOnAction(e -> toggleSidebar());
        brandRow = new HBox(6, brandMark, brandSpacer, collapseButton);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("brand");

        // navigation rail — destinations keep a selected state; Compare is an
        // action that opens the comparison sheet, so it never stays "current".
        ToggleGroup group = new ToggleGroup();
        navButton("Discover", Feather.COMPASS, group, ViewNavigator.DISCOVERY_VIEW);
        navButton("My Applications", Feather.CHECK_SQUARE, group, ViewNavigator.TRACKER_VIEW);
        Button compareNav = navAction("Compare", Feather.COLUMNS, () ->
                ViewNavigator.show(ViewNavigator.DISCOVERY_VIEW, controller -> {
                    if (controller instanceof DiscoveryController discovery) {
                        discovery.openCompareFromSidebar();
                    }
                }));
        installTooltip(compareNav, "Compare the scholarships you ticked");
        navButton("My Profile", Feather.USER, group, ViewNavigator.WIZARD_VIEW);
        adminButton = navButton("Admin Portal", Feather.SHIELD, group, ViewNavigator.ADMIN_DASHBOARD_VIEW);

        VBox navBox = new VBox(4);
        navBox.getChildren().addAll(NAV_BUTTONS.get(0), NAV_BUTTONS.get(1), compareNav,
                NAV_BUTTONS.get(2), adminButton);
        VBox.setVgrow(navBox, Priority.ALWAYS);

        // footer: session chip + log out + theme toggle + login
        userChip = new HBox(10, FontIcon.of(Feather.USER, 18),
                new VBox(1, nameLabel = styledLabel("user-name"), roleLabel = styledLabel("user-role")));
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.getStyleClass().add("user-chip");

        logoutButton = footerButton("Log out", Feather.LOG_OUT);
        logoutButton.setOnAction(e -> handleAuth());

        themeButton = footerButton("Light", Feather.MOON);
        themeButton.setOnAction(e -> toggleTheme());

        authButton = footerButton("Log in", Feather.LOG_IN);
        authButton.setOnAction(e -> handleAuth());

        VBox footer = new VBox(8, userChip, logoutButton, themeButton, authButton);
        sidebar.getChildren().addAll(brandRow, new Separator(), navBox, footer);
        return sidebar;
    }

    private static Label styledLabel(String styleClass) {
        Label label = new Label();
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static Button footerButton(String text, Feather icon) {
        Button button = new Button(text, FontIcon.of(icon, 18));
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    /** Installs (or retargets) a tooltip that also covers the icon-only collapsed rail. */
    private static void installTooltip(Node node, String text) {
        Tooltip tip = TOOLTIPS.get(node);
        if (tip == null) {
            tip = new Tooltip(text);
            tip.setShowDelay(Duration.millis(350));
            Tooltip.install(node, tip);
            TOOLTIPS.put(node, tip);
        } else {
            tip.setText(text);
        }
    }

    private static ToggleButton navButton(String text, Feather icon, ToggleGroup group,
                                         String fxmlPath) {
        int index = NAV_BUTTONS.size();
        ToggleButton button = new ToggleButton(text, FontIcon.of(icon, 18));
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setToggleGroup(group);
        NAV_BUTTONS.add(button);
        NAV_TITLES.put(button, text);
        NAV_VIEWS.put(button, fxmlPath);
        // Tooltips keep the icon-only collapsed rail legible and expose the
        // Ctrl+1…4 accelerators that mirror the rail order.
        installTooltip(button, index < 4 ? text + "  (Ctrl+" + (index + 1) + ")" : text);
        button.setOnAction(e -> ViewNavigator.show(fxmlPath));
        return button;
    }

    /** A one-shot nav action (no toggle state) that still looks like the rail. */
    private static Button navAction(String text, Feather icon, Runnable action) {
        Button button = new Button(text, FontIcon.of(icon, 18));
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        // Registered like the toggle destinations so the 68px rail drops its
        // label instead of ellipsizing it to "…" next to the icon.
        NAV_TITLES.put(button, text);
        installTooltip(button, text);
        button.setOnAction(e -> action.run());
        return button;
    }

    // ------------------------------------------------------------------
    // Interactions
    // ------------------------------------------------------------------

    private static void toggleSidebar() {
        collapsed = !collapsed;
        double target = collapsed ? SIDEBAR_COLLAPSED : SIDEBAR_EXPANDED;
        if (collapsed) {
            sidebar.getStyleClass().add("collapsed");
        } else {
            sidebar.getStyleClass().remove("collapsed");
        }
        applyCollapsedChrome();
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(sidebar.prefWidthProperty(), target, Interpolator.EASE_BOTH)));
        timeline.setOnFinished(e -> applyCollapsedText());
        timeline.play();
    }

    private static void applyCollapsedText() {
        NAV_TITLES.forEach((node, title) -> node.setText(collapsed ? "" : title));
        brandLabel.setText(collapsed ? "" : "iskolarPath");
        nameLabel.setVisible(!collapsed);
        nameLabel.setManaged(!collapsed);
        roleLabel.setVisible(!collapsed);
        roleLabel.setManaged(!collapsed);
        refreshSessionUi();
    }

    /**
     * Expanded: brand mark + toggle. Collapsed: the 68px rail cannot fit both,
     * so the brand mark is removed from layout and the toggle is centered.
     */
    private static void applyCollapsedChrome() {
        brandMark.setVisible(!collapsed);
        brandMark.setManaged(!collapsed);
        brandSpacer.setVisible(!collapsed);
        brandSpacer.setManaged(!collapsed);
        brandRow.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        brandLabel.setText(collapsed ? "" : "iskolarPath");
        collapseButton.setTooltip(new Tooltip(collapsed ? "Expand sidebar" : "Collapse sidebar"));
        collapseButton.setAccessibleText(collapsed ? "Expand sidebar" : "Collapse sidebar");
    }

    private static void toggleTheme() {
        ThemeManager.toggle();
        SessionManager.current().ifPresent(session ->
                DaoFactory.userDao().updateThemePreference(session.userId(), ThemeManager.current()));
        refreshSessionUi();
    }

    private static void handleAuth() {
        if (SessionManager.current().isPresent()) {
            SessionManager.logout();
            DiscoveryController.clearCompareSelection();
            JourneyRail.setAwarded(false);
            refreshSessionUi();
            NAV_BUTTONS.get(0).fire();
        } else {
            ViewNavigator.show(ViewNavigator.LOGIN_VIEW);
        }
    }

    /**
     * Re-syncs the session chip, log out button, login button and admin-nav
     * visibility with the current session + theme. Called after
     * login/logout/theme changes and whenever the sidebar expands.
     */
    public static void refreshSessionUi() {
        var session = SessionManager.current();
        boolean loggedIn = session.isPresent();

        logoutButton.setVisible(loggedIn);
        logoutButton.setManaged(loggedIn);
        logoutButton.setText(collapsed ? "" : "Log out");

        authButton.setVisible(!loggedIn);
        authButton.setManaged(!loggedIn);
        authButton.setText(collapsed ? "" : "Log in");
        authButton.setGraphic(FontIcon.of(Feather.LOG_IN, 18));

        boolean admin = loggedIn && session.get().role() == Role.ADMIN;
        adminButton.setVisible(admin);
        adminButton.setManaged(admin);

        if (loggedIn) {
            nameLabel.setText(session.get().email());
            roleLabel.setText(session.get().role() == Role.ADMIN ? "Administrator" : "Student");
        } else {
            nameLabel.setText("Guest");
            roleLabel.setText("Browsing as guest");
        }
        installTooltip(userChip, loggedIn
                ? session.get().email() + " · " + SessionManager.current().get().role().name()
                : "Not signed in — browse freely, log in to track applications");

        themeButton.setGraphic(FontIcon.of(
                ThemeManager.current() == ThemePref.DARK ? Feather.SUN : Feather.MOON, 18));
        themeButton.setText(collapsed ? "" : (ThemeManager.current() == ThemePref.DARK ? "Dark" : "Light"));
        installTooltip(themeButton, ThemeManager.current() == ThemePref.DARK
                ? "Switch to light theme" : "Switch to dark theme");
    }
}
