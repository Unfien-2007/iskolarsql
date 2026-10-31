package com.iskolar.view;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Objects;
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
        dialog.setTitle("Sign in to the catalog server");
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                ServerAuthDialog.class.getResource("/css/app.css"),
                "css/app.css missing from classpath").toExternalForm());

        ButtonType signIn = new ButtonType("Sign in", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signIn, ButtonType.CANCEL);

        Label note = new Label("Admin edits are stored on the central server.");
        note.getStyleClass().add("details-note");
        note.setWrapText(true);
        Label emailLabel = new Label("Signed in as " + email);
        emailLabel.getStyleClass().add("details-provider");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Server password");

        VBox content = new VBox(10, note, emailLabel, passwordField);
        content.getStyleClass().add("details-root");
        content.setPrefWidth(340);
        dialog.getDialogPane().setContent(content);

        var signInButton = dialog.getDialogPane().lookupButton(signIn);
        signInButton.setDisable(true);
        passwordField.textProperty().addListener((obs, old, value) ->
                signInButton.setDisable(value == null || value.isBlank()));

        ThemedDialog.install(dialog, owner, "Catalog server");

        dialog.setResultConverter(button -> button == signIn ? passwordField.getText() : null);
        return dialog.showAndWait().filter(password -> password != null && !password.isBlank());
    }
}
