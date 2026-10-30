package com.iskolar.view;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Small sign-in prompt for the central catalog server: shows the admin's
 * email (from the local session) and collects the server password. Used by
 * the admin write gateway before its first server write of the session.
 */
public final class ServerAuthDialog {

    private ServerAuthDialog() {
    }

    public static Optional<String> promptPassword(Window owner, String email) {
        Dialog<String> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("Sign in to the catalog server");
        dialog.setHeaderText("Admin edits are stored on the central server.");

        ButtonType signIn = new ButtonType("Sign in", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signIn, ButtonType.CANCEL);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Server password");
        Label emailLabel = new Label("Signed in as " + email);
        emailLabel.getStyleClass().add("details-provider");
        VBox content = new VBox(10, emailLabel, passwordField);
        content.getStyleClass().add("details-root");
        content.setPrefWidth(320);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().add(ServerAuthDialog.class
                .getResource("/css/app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-root");
        if (com.iskolar.util.ThemeManager.isDark()) {
            dialog.getDialogPane().getStyleClass().add("dark");
        }

        dialog.setResultConverter(button -> button == signIn ? passwordField.getText() : null);
        Optional<String> result = dialog.showAndWait();
        return result.filter(password -> password != null && !password.isBlank());
    }
}
