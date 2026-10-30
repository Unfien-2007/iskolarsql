package com.iskolar.view;

import com.iskolar.service.AuthResult;
import com.iskolar.service.ServiceFactory;
import com.iskolar.util.ThemeManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Objects;

/**
 * Two-step self-service password reset: (1) request a token by email, then
 * (2) exchange the token for a new password. Both steps require the central
 * server; in dev mode the server prints the token to its log.
 */
public final class ForgotPasswordDialog {

    private ForgotPasswordDialog() {
    }

    public static void show(Window owner, String prefilledEmail) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Reset password");
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                ForgotPasswordDialog.class.getResource("/css/app.css"),
                "css/app.css missing from classpath").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-root");
        if (ThemeManager.isDark()) {
            dialog.getDialogPane().getStyleClass().add("dark");
        }

        VBox content = new VBox(10);
        content.getStyleClass().add("details-root");
        content.setPadding(new Insets(4));
        content.setPrefWidth(420);

        buildRequestStep(content, prefilledEmail);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // Step 1 — request a token by email
    // ------------------------------------------------------------------

    private static void buildRequestStep(VBox content, String prefilledEmail) {
        Label note = note("Enter your account email and we will send you a reset link.");
        TextField emailField = new TextField(prefilledEmail == null ? "" : prefilledEmail);
        emailField.setPromptText("you@school.edu.ph");
        Button sendButton = new Button("Send reset link");
        sendButton.setMaxWidth(Double.MAX_VALUE);
        sendButton.getStyleClass().add("primary-button");
        Label errorLabel = errorLabel();

        content.getChildren().setAll(
                note,
                fieldLabel("Email"),
                emailField,
                sendButton,
                errorLabel);

        sendButton.setOnAction(e -> {
            String email = emailField.getText();
            if (email.isBlank()) {
                show(errorLabel, "Enter a valid email address.");
                return;
            }
            setBusy(sendButton, emailField, errorLabel, true, "Sending…", "Send reset link");
            Thread worker = new Thread(() -> {
                AuthResult result = ServiceFactory.authService().forgotPassword(email);
                Platform.runLater(() -> {
                    if (!result.success()) {
                        setBusy(sendButton, emailField, errorLabel, false,
                                "Send reset link", "Send reset link");
                        show(errorLabel, result.message());
                        return;
                    }
                    // Step 2 — the token arrives via email (dev: server log).
                    buildResetStep(content, result.message());
                });
            }, "iskolar-forgot-password");
            worker.setDaemon(true);
            worker.start();
        });
    }

    // ------------------------------------------------------------------
    // Step 2 — exchange the token for a new password
    // ------------------------------------------------------------------

    private static void buildResetStep(VBox content, String serverMessage) {
        Label note = note(serverMessage
                + " In development the token is printed in the server log — paste it below.");
        TextField tokenField = new TextField();
        tokenField.setPromptText("Reset token");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("New password (min 8 characters)");
        Button resetButton = new Button("Reset password");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.getStyleClass().add("primary-button");
        Label errorLabel = errorLabel();

        content.getChildren().setAll(
                note,
                fieldLabel("Reset token"),
                tokenField,
                fieldLabel("New password"),
                passwordField,
                resetButton,
                errorLabel);

        resetButton.setOnAction(e -> {
            String token = tokenField.getText();
            String newPassword = passwordField.getText();
            if (token.isBlank()) {
                show(errorLabel, "Enter the reset token from your email.");
                return;
            }
            if (newPassword.length() < 8) {
                show(errorLabel, "Password must be at least 8 characters.");
                return;
            }
            setBusy(resetButton, tokenField, errorLabel, true, "Resetting…", "Reset password");
            passwordField.setDisable(true);
            Thread worker = new Thread(() -> {
                AuthResult result = ServiceFactory.authService().resetPassword(token, newPassword);
                Platform.runLater(() -> {
                    if (!result.success()) {
                        setBusy(resetButton, tokenField, errorLabel, false,
                                "Reset password", "Reset password");
                        passwordField.setDisable(false);
                        show(errorLabel, result.message());
                        return;
                    }
                    tokenField.setDisable(true);
                    passwordField.setDisable(true);
                    resetButton.setDisable(true);
                    resetButton.setText("Password updated");
                    note.setText(result.message() + " You can now sign in.");
                });
            }, "iskolar-reset-password");
            worker.setDaemon(true);
            worker.start();
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("details-note");
        return label;
    }

    private static Label errorLabel() {
        Label label = new Label();
        label.getStyleClass().add("error-label");
        label.setWrapText(true);
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    private static void show(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private static void setBusy(Button button, TextField field, Label errorLabel,
                                boolean busy, String busyText, String idleText) {
        button.setDisable(busy);
        field.setDisable(busy);
        button.setText(busy ? busyText : idleText);
        if (busy) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }
}
