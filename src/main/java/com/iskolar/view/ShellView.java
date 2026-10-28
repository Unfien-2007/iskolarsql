package com.iskolar.view;

import com.iskolar.dao.DaoFactory;
import com.iskolar.model.Role;
import com.iskolar.model.ThemePref;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ThemeManager;
import com.iskolar.util.ViewNavigator;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The single-window shell: a collapsible left navigation rail (brand, nav
 * items, session chip, theme toggle, login/logout) around a dynamic center
 * area managed by {@link ViewNavigator}.
 */
public final class ShellView {

    public static final double SIDEBAR_EXPANDED = 220;
    public static final double SIDEBAR_COLLAPSED = 68;

    private static VBox sidebar;
    private static HBox topBar;
    private static Label topBarUser;
    private static Label brandLabel;
    private static Label nameLabel;
    private static Label roleLabel;
    private static Button authButton;
    private static Button themeButton;
    private static ToggleButton adminButton;
    private static final List<ToggleButton> NAV_BUTTONS = new ArrayList<>();
    private static final Map<Labeled, String> NAV_TITLES = new HashMap<>();
    private static boolean collapsed;

    private ShellView() {
    }

    public static BorderPane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        ViewNavigator.init(root);
        root.setLeft(buildSidebar());
        root.setTop(buildTopBar());
        refreshSessionUi();
        NAV_BUTTONS.get(0).fire(); // land on Discover
        return root;
    }

    /** Always-visible header bar: who is signed in + a prominent Log out button. */
    private static HBox buildTopBar() {
        topBarUser = new Label();
        topBarUser.getStyleClass().add("topbar-user");

        Button logoutButton = new Button("Log out", FontIcon.of(Feather.LOG_OUT, 16));
        logoutButton.getStyleClass().addAll("ghost-button", "topbar-logout");
        logoutButton.setOnAction(e -> handleAuth());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBar = new HBox(12, spacer, topBarUser, logoutButton);
        topBar.getStyleClass().add("topbar");
        topBar.setAlignment(Pos.CENTER_RIGHT);
        return topBar;
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
        HBox brand = new HBox(10, FontIcon.of(Feather.BOOK_OPEN, 20), brandLabel = new Label("Iskolar"));
        brand.setAlignment(Pos.CENTER_LEFT);
        brandLabel.getStyleClass().add("brand-title");
        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);
        Button collapseButton = new Button("", FontIcon.of(Feather.MENU, 18));
        collapseButton.getStyleClass().add("icon-button");
        collapseButton.setOnAction(e -> toggleSidebar());
        HBox brandRow = new HBox(6, brand, brandSpacer, collapseButton);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("brand");

        // navigation rail
        ToggleGroup group = new ToggleGroup();
        navButton("Discover", Feather.COMPASS, group, ViewNavigator.DISCOVERY_VIEW, null);
        navButton("My Applications", Feather.CHECK_SQUARE, group, ViewNavigator.TRACKER_VIEW, null);
        navButton("Compare", Feather.COLUMNS, group, ViewNavigator.DISCOVERY_VIEW, null);
        navButton("My Profile", Feather.USER, group, ViewNavigator.WIZARD_VIEW, null);
        adminButton = navButton("Admin Portal", Feather.SHIELD, group, ViewNavigator.ADMIN_DASHBOARD_VIEW, null);

        VBox navBox = new VBox(4);
        navBox.getChildren().addAll(NAV_BUTTONS);
        VBox.setVgrow(navBox, Priority.ALWAYS);

        // footer: session chip + theme toggle + login/logout
        HBox chip = new HBox(10, FontIcon.of(Feather.USER, 18),
                new VBox(1, nameLabel = styledLabel("user-name"), roleLabel = styledLabel("user-role")));
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("user-chip");

        themeButton = footerButton("Light", Feather.MOON);
        themeButton.setOnAction(e -> toggleTheme());

        authButton = footerButton("Log in", Feather.LOG_IN);
        authButton.setOnAction(e -> handleAuth());

        VBox footer = new VBox(8, chip, themeButton, authButton);
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

    private static ToggleButton navButton(String text, Feather icon, ToggleGroup group,
                                          String fxmlPath, Consumer<Object> controllerInit) {
        ToggleButton button = new ToggleButton(text, FontIcon.of(icon, 18));
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setToggleGroup(group);
        NAV_BUTTONS.add(button);
        NAV_TITLES.put(button, text);
        button.setOnAction(e -> ViewNavigator.show(fxmlPath, controllerInit));
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
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(sidebar.prefWidthProperty(), target, Interpolator.EASE_BOTH)));
        timeline.setOnFinished(e -> applyCollapsedText());
        timeline.play();
    }

    private static void applyCollapsedText() {
        NAV_TITLES.forEach((node, title) -> node.setText(collapsed ? "" : title));
        brandLabel.setText(collapsed ? "" : "Iskolar");
        nameLabel.setVisible(!collapsed);
        nameLabel.setManaged(!collapsed);
        roleLabel.setVisible(!collapsed);
        roleLabel.setManaged(!collapsed);
        refreshSessionUi();
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
            refreshSessionUi();
            NAV_BUTTONS.get(0).fire();
        } else {
            ViewNavigator.show(ViewNavigator.LOGIN_VIEW);
        }
    }

    /**
     * Re-syncs the session chip, auth button and admin-nav visibility with
     * the current session + theme. Called after login/logout/theme changes
     * and whenever the sidebar expands.
     */
    public static void refreshSessionUi() {
        var session = SessionManager.current();
        boolean loggedIn = session.isPresent();

        topBar.setVisible(loggedIn);
        topBar.setManaged(loggedIn);
        if (loggedIn) {
            topBarUser.setText(session.get().email() + "  ·  " + session.get().role().name());
        }

        authButton.setText(collapsed ? "" : (loggedIn ? "Log out" : "Log in"));
        authButton.setGraphic(FontIcon.of(loggedIn ? Feather.LOG_OUT : Feather.LOG_IN, 18));

        boolean admin = loggedIn && session.get().role() == Role.ADMIN;
        adminButton.setVisible(admin);
        adminButton.setManaged(admin);

        if (loggedIn) {
            nameLabel.setText(session.get().email());
            roleLabel.setText(session.get().role().name());
        } else {
            nameLabel.setText("Guest");
            roleLabel.setText("Browse freely — no login needed");
        }

        themeButton.setGraphic(FontIcon.of(
                ThemeManager.current() == ThemePref.DARK ? Feather.SUN : Feather.MOON, 18));
        themeButton.setText(collapsed ? "" : (ThemeManager.current() == ThemePref.DARK ? "Dark" : "Light"));
    }
}
