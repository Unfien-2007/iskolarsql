package com.iskolar.view;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Window chrome for the undecorated stage: minimise / maximise / close
 * buttons, drag-to-move with double-click to maximise, and edge resizing.
 *
 * <p>The app draws its own caption in the top band, so the native Windows
 * title bar (which stays light even in dark mode) is gone. Everything the
 * native frame used to provide is reimplemented here: the OS still handles
 * Alt+F4 and the taskbar entry, while the code below covers pointer input.</p>
 *
 * <p>Methods are null-safe about the stage so the offscreen UI renderer can
 * build the shell without a window.</p>
 */
public final class WindowChrome {

    /** Pixels from a window edge that start a resize. */
    private static final double EDGE = 6;
    /** Double-clicking the top band toggles maximise. */
    private static final int MAXIMISE_CLICKS = 2;

    private static final int NONE = 0;
    private static final int TOP = 1;
    private static final int BOTTOM = 2;
    private static final int LEFT = 4;
    private static final int RIGHT = 8;

    private static Stage stage;
    private static int zone = NONE;
    private static boolean resizing;
    private static boolean dragging;
    private static double pressScreenX;
    private static double pressScreenY;
    private static double startX;
    private static double startY;
    private static double startWidth;
    private static double startHeight;
    private static double dragOffsetX;
    private static double dragOffsetY;
    private static double restoreX;
    private static double restoreY;
    private static double restoreWidth;
    private static double restoreHeight;

    private WindowChrome() {
    }

    /** Binds the chrome to the window it controls (call before building the shell). */
    public static void install(Stage window) {
        stage = window;
    }

    /** The minimise / maximise / close cluster that ends the top band. */
    public static HBox controls() {
        HBox box = new HBox(2);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.getStyleClass().add("window-controls");

        Button minimise = windowButton(Feather.MINUS, "Minimise");
        minimise.setOnAction(e -> {
            if (stage != null) {
                stage.setIconified(true);
            }
        });

        Button maximise = windowButton(Feather.SQUARE, "Maximise");
        maximise.setOnAction(e -> toggleMaximise(maximise));

        Button close = windowButton(Feather.X, "Close");
        close.getStyleClass().add("window-close");
        close.setOnAction(e -> requestClose());

        box.getChildren().addAll(minimise, maximise, close);
        return box;
    }

    /** Adds resize-handling and cursor feedback to the scene. */
    public static void attach(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (stage == null || resizing) {
                return;
            }
            int at = zoneAt(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight());
            scene.getRoot().setCursor(cursorFor(at));
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (stage == null || e.getButton() != MouseButton.PRIMARY
                    || stage.isMaximized() || isInteractive(e.getTarget())) {
                return;
            }
            int at = zoneAt(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight());
            if (at == NONE) {
                return;
            }
            zone = at;
            resizing = true;
            pressScreenX = e.getScreenX();
            pressScreenY = e.getScreenY();
            startX = stage.getX();
            startY = stage.getY();
            startWidth = stage.getWidth();
            startHeight = stage.getHeight();
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!resizing) {
                return;
            }
            double dx = e.getScreenX() - pressScreenX;
            double dy = e.getScreenY() - pressScreenY;
            double x = startX;
            double y = startY;
            double width = startWidth;
            double height = startHeight;

            if ((zone & RIGHT) != 0) {
                width = startWidth + dx;
            }
            if ((zone & LEFT) != 0) {
                width = startWidth - dx;
                x = startX + dx;
            }
            if ((zone & BOTTOM) != 0) {
                height = startHeight + dy;
            }
            if ((zone & TOP) != 0) {
                height = startHeight - dy;
                y = startY + dy;
            }

            double minWidth = stage.getMinWidth() > 0 ? stage.getMinWidth() : 320;
            double minHeight = stage.getMinHeight() > 0 ? stage.getMinHeight() : 240;
            if (width < minWidth) {
                if ((zone & LEFT) != 0) {
                    x -= minWidth - width;
                }
                width = minWidth;
            }
            if (height < minHeight) {
                if ((zone & TOP) != 0) {
                    y -= minHeight - height;
                }
                height = minHeight;
            }

            stage.setX(x);
            stage.setY(y);
            stage.setWidth(width);
            stage.setHeight(height);
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!resizing) {
                return;
            }
            resizing = false;
            zone = NONE;
            e.consume();
        });
    }

    /** Makes a band of the UI behave like a title bar. */
    public static void makeDraggable(Node area) {
        area.setOnMousePressed(e -> {
            if (stage == null || e.getButton() != MouseButton.PRIMARY
                    || stage.isMaximized() || isInteractive(e.getTarget())) {
                return;
            }
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
            dragging = true;
        });
        area.setOnMouseDragged(e -> {
            if (!dragging || stage == null || stage.isMaximized()) {
                return;
            }
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });
        area.setOnMouseReleased(e -> dragging = false);
        area.setOnMouseClicked(e -> {
            if (e.getClickCount() == MAXIMISE_CLICKS && !isInteractive(e.getTarget())) {
                toggleMaximise(null);
            }
        });
    }

    // ------------------------------------------------------------------
    // Window actions
    // ------------------------------------------------------------------

    private static void toggleMaximise(Button button) {
        if (stage == null) {
            return;
        }
        if (!stage.isMaximized()) {
            restoreX = stage.getX();
            restoreY = stage.getY();
            restoreWidth = stage.getWidth();
            restoreHeight = stage.getHeight();
            stage.setMaximized(true);
        } else {
            stage.setMaximized(false);
            // Some window managers do not put an undecorated stage back where
            // it was; re-apply the remembered bounds once the flag settles.
            Platform.runLater(() -> {
                if (restoreWidth > 0 && restoreHeight > 0) {
                    stage.setX(restoreX);
                    stage.setY(restoreY);
                    stage.setWidth(restoreWidth);
                    stage.setHeight(restoreHeight);
                }
            });
        }
        if (button != null) {
            button.setTooltip(new Tooltip(stage.isMaximized() ? "Restore" : "Maximise"));
        }
    }

    private static void requestClose() {
        if (stage == null) {
            return;
        }
        // Firing the close request runs the app's "save window size" handler,
        // then closes unless something vetoed it.
        WindowEvent event = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        stage.fireEvent(event);
        if (!event.isConsumed()) {
            stage.close();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Button windowButton(Feather icon, String label) {
        Button button = new Button();
        button.setGraphic(FontIcon.of(icon, 14));
        button.getStyleClass().add("window-button");
        button.setFocusTraversable(false);
        button.setAccessibleText(label);
        Tooltip tip = new Tooltip(label);
        tip.setShowDelay(Duration.millis(400));
        button.setTooltip(tip);
        return button;
    }

    /** Resize zones never swallow presses meant for a control underneath. */
    private static boolean isInteractive(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof ButtonBase
                    || current instanceof TextInputControl
                    || current instanceof ComboBoxBase
                    || current instanceof ScrollBar) {
                return true;
            }
        }
        return false;
    }

    private static int zoneAt(double x, double y, double width, double height) {
        if (x < 0 || y < 0 || x > width || y > height) {
            return NONE;
        }
        int at = NONE;
        if (y <= EDGE) {
            at |= TOP;
        } else if (y >= height - EDGE) {
            at |= BOTTOM;
        }
        if (x <= EDGE) {
            at |= LEFT;
        } else if (x >= width - EDGE) {
            at |= RIGHT;
        }
        return at;
    }

    private static Cursor cursorFor(int at) {
        return switch (at) {
            case TOP -> Cursor.N_RESIZE;
            case BOTTOM -> Cursor.S_RESIZE;
            case LEFT -> Cursor.W_RESIZE;
            case RIGHT -> Cursor.E_RESIZE;
            case TOP | LEFT -> Cursor.NW_RESIZE;
            case TOP | RIGHT -> Cursor.NE_RESIZE;
            case BOTTOM | LEFT -> Cursor.SW_RESIZE;
            case BOTTOM | RIGHT -> Cursor.SE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }
}
