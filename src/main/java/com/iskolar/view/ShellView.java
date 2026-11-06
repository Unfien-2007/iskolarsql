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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
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
    private static final Map<ToggleButton, String> NAV_VIEWS = new HashMap<>();
    private static final Map<Node, Tooltip> TOOLTIPS = new HashMap<>();
    /** Every sidebar item drawn as an icon tile (with a short label when collapsed). */
    private static final List<RailItem> RAIL = new ArrayList<>();
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

    /**
     * Top area: the Path journey rail plus the window controls. The band is
     * the app's own caption — the stage is undecorated, so it also handles
     * dragging and double-click-to-maximise.
     */
    /** Top band: centred numbered stepper, window controls overlaid at the right. */
    private static StackPane buildTopArea() {
        HBox rail = JourneyRail.build();
        StackPane bar = new StackPane(rail);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("top-bar");
        HBox controls = WindowChrome.controls();
        StackPane.setAlignment(controls, Pos.CENTER_RIGHT);
        StackPane.setMargin(controls, new Insets(0, 2, 0, 0));
        bar.getChildren().add(controls);
        WindowChrome.makeDraggable(bar);
        return bar;
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
        navButton("Discover", "Discover", Feather.COMPASS, group, ViewNavigator.DISCOVERY_VIEW);
        navButton("My Applications", "Applied", Feather.CHECK_SQUARE, group,
                ViewNavigator.TRACKER_VIEW);
        Button compareNav = navAction("Compare", "Compare", Feather.COLUMNS, () ->
                ViewNavigator.show(ViewNavigator.DISCOVERY_VIEW, controller -> {
                    if (controller instanceof DiscoveryController discovery) {
                        discovery.openCompareFromSidebar();
                    }
                }));
        installTooltip(compareNav, "Compare the scholarships you ticked");
        navButton("My Profile", "Profile", Feather.USER, group, ViewNavigator.WIZARD_VIEW);
        adminButton = navButton("Admin Portal", "Admin", Feather.SHIELD, group,
                ViewNavigator.ADMIN_DASHBOARD_VIEW);

        VBox navBox = new VBox(4);
        navBox.getChildren().addAll(NAV_BUTTONS.get(0), NAV_BUTTONS.get(1), compareNav,
                NAV_BUTTONS.get(2), adminButton);
        VBox.setVgrow(navBox, Priority.ALWAYS);

        // footer: session chip + log out + theme toggle + login
        userChip = new HBox(10, FontIcon.of(Feather.USER, 18),
                new VBox(1, nameLabel = styledLabel("user-name"), roleLabel = styledLabel("user-role")));
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.getStyleClass().add("user-chip");

        logoutButton = footerButton("Log out", "Log out", Feather.LOG_OUT);
        logoutButton.setOnAction(e -> handleAuth());

        themeButton = footerButton("Light", "Theme", Feather.MOON);
        themeButton.setOnAction(e -> toggleTheme());

        authButton = footerButton("Log in", "Log in", Feather.LOG_IN);
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

    /** One rail entry: the button, its icon tile and the short collapsed label. */
    private static final class RailItem {
        private final ButtonBase node;
        private final StackPane tile;
        private final Label mini;
        private String title;

        private RailItem(ButtonBase node, StackPane tile, Label mini, String title) {
            this.node = node;
            this.tile = tile;
            this.mini = mini;
            this.title = title;
        }
    }

    /** Gives a sidebar button the icon-tile treatment used by the collapsed rail. */
    private static void styleRailItem(ButtonBase node, String title, String shortTitle,
                                      Feather icon) {
        StackPane tile = new StackPane(FontIcon.of(icon, 18));
        tile.getStyleClass().add("nav-tile");
        Label mini = new Label(shortTitle);
        mini.getStyleClass().add("nav-mini");
        mini.setVisible(false);
        // Expanded rail: the short label is hidden, so it must not reserve
        // width/height either — otherwise each item's graphic takes the width
        // of its own mini text and the labels stop lining up.
        mini.setManaged(false);
        VBox graphic = new VBox(3, tile, mini);
        graphic.setAlignment(Pos.CENTER);

        node.setGraphic(graphic);
        node.setText(title);
        node.setContentDisplay(ContentDisplay.LEFT);
        node.getStyleClass().add("nav-button");
        node.setMaxWidth(Double.MAX_VALUE);
        RAIL.add(new RailItem(node, tile, mini, title));
    }

    private static Button footerButton(String text, String shortTitle, Feather icon) {
        Button button = new Button();
        styleRailItem(button, text, shortTitle, icon);
        return button;
    }

    private static void setRailTitle(ButtonBase node, String title) {
        for (RailItem item : RAIL) {
            if (item.node == node) {
                item.title = title;
                if (!collapsed) {
                    node.setText(title);
                }
                return;
            }
        }
    }

    private static void setRailIcon(ButtonBase node, Feather icon) {
        for (RailItem item : RAIL) {
            if (item.node == node) {
                item.tile.getChildren().setAll(FontIcon.of(icon, 18));
                return;
            }
        }
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

    private static ToggleButton navButton(String text, String shortTitle, Feather icon,
                                         ToggleGroup group, String fxmlPath) {
        int index = NAV_BUTTONS.size();
        ToggleButton button = new ToggleButton();
        button.setToggleGroup(group);
        NAV_BUTTONS.add(button);
        NAV_VIEWS.put(button, fxmlPath);
        styleRailItem(button, text, shortTitle, icon);
        // Tooltips keep the icon-only collapsed rail legible and expose the
        // Ctrl+1…4 accelerators that mirror the rail order.
        installTooltip(button, index < 4 ? text + "  (Ctrl+" + (index + 1) + ")" : text);
        button.setOnAction(e -> ViewNavigator.show(fxmlPath));
        return button;
    }

    /** A one-shot nav action (no toggle state) that still looks like the rail. */
    private static Button navAction(String text, String shortTitle, Feather icon, Runnable action) {
        Button button = new Button();
        styleRailItem(button, text, shortTitle, icon);
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
        for (RailItem item : RAIL) {
            item.node.setText(collapsed ? "" : item.title);
            item.mini.setVisible(collapsed);
            item.mini.setManaged(collapsed);
            item.node.setContentDisplay(collapsed ? ContentDisplay.TOP : ContentDisplay.LEFT);
            item.node.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        }
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
        setRailTitle(logoutButton, "Log out");

        authButton.setVisible(!loggedIn);
        authButton.setManaged(!loggedIn);
        setRailTitle(authButton, "Log in");

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

        setRailIcon(themeButton,
                ThemeManager.current() == ThemePref.DARK ? Feather.SUN : Feather.MOON);
        setRailTitle(themeButton, ThemeManager.current() == ThemePref.DARK ? "Dark" : "Light");
        installTooltip(themeButton, ThemeManager.current() == ThemePref.DARK
                ? "Switch to light theme" : "Switch to dark theme");
    }
}
