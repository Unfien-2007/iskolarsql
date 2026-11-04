package com.iskolar.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A password field with a reveal toggle.
 *
 * <p>Editable state lives on a hidden {@link PasswordField}; a plain
 * {@link TextField} mirrors it through a bidirectional binding and is swapped
 * in when the user asks to see what they typed. Both inputs share one text
 * value, so toggling never loses characters, and the caret is moved to the end
 * so typing continues naturally.</p>
 *
 * <p>Used by the login and register screens and by the two password dialogs.</p>
 */
public final class PasswordInput extends HBox {

    private final PasswordField masked = new PasswordField();
    private final TextField plain = new TextField();
    private final Button toggle = new Button();

    public PasswordInput() {
        super(6);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("password-input");

        masked.setMaxWidth(Double.MAX_VALUE);
        plain.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(masked, Priority.ALWAYS);
        HBox.setHgrow(plain, Priority.ALWAYS);

        plain.setVisible(false);
        plain.setManaged(false);
        plain.promptTextProperty().bind(masked.promptTextProperty());
        plain.textProperty().bindBidirectional(masked.textProperty());

        toggle.getStyleClass().add("icon-button");
        toggle.setFocusTraversable(true);
        toggle.setOnAction(e -> toggleReveal());
        updateToggle(false);

        getChildren().addAll(masked, plain, toggle);
    }

    // ---- PasswordField-compatible surface -----------------------------

    public String getText() {
        return masked.getText();
    }

    public void setText(String text) {
        masked.setText(text);
    }

    public void clear() {
        masked.clear();
    }

    public void setPromptText(String prompt) {
        masked.setPromptText(prompt);
    }

    public String getPromptText() {
        return masked.getPromptText();
    }

    /** Runs on Enter in either the masked or the revealed field. */
    public void setOnAction(EventHandler<ActionEvent> handler) {
        masked.setOnAction(handler);
        plain.setOnAction(handler);
    }

    /** The single text value, for listeners and validation bindings. */
    public javafx.beans.property.StringProperty textProperty() {
        return masked.textProperty();
    }

    /** Focuses the input that is currently showing. */
    @Override
    public void requestFocus() {
        (plain.isVisible() ? plain : masked).requestFocus();
    }

    // ---- Reveal ------------------------------------------------------

    private void toggleReveal() {
        boolean reveal = !plain.isVisible();
        masked.setVisible(!reveal);
        masked.setManaged(!reveal);
        plain.setVisible(reveal);
        plain.setManaged(reveal);
        updateToggle(reveal);

        TextInputControl target = reveal ? plain : masked;
        target.requestFocus();
        String text = target.getText();
        target.positionCaret(text == null ? 0 : text.length());
    }

    private void updateToggle(boolean revealed) {
        toggle.setGraphic(FontIcon.of(revealed ? Feather.EYE_OFF : Feather.EYE, 16));
        String label = revealed ? "Hide password" : "Show password";
        if (toggle.getTooltip() == null) {
            Tooltip tip = new Tooltip(label);
            tip.setShowDelay(Duration.millis(300));
            toggle.setTooltip(tip);
        } else {
            toggle.getTooltip().setText(label);
        }
        toggle.setAccessibleText(label);
    }
}
