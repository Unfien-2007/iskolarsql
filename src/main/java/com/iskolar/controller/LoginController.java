package com.iskolar.controller;

import com.iskolar.service.AuthResult;
import com.iskolar.service.ServiceFactory;
import com.iskolar.util.ViewNavigator;
import com.iskolar.view.ShellView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Login screen. Credentials are verified against the central catalog server
 * (with offline fallback) on a background thread so the UI never freezes.
 */
public class LoginController {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button loginButton;
    @FXML
    private Hyperlink registerLink;

    @FXML
    private void initialize() {
        loginButton.setOnAction(e -> login());
        emailField.setOnAction(e -> login());
        passwordField.setOnAction(e -> login());
        registerLink.setOnAction(e -> ViewNavigator.show(ViewNavigator.REGISTER_VIEW));
    }

    private void login() {
        String email = emailField.getText();
        String password = passwordField.getText();
        if (email.isBlank() || password.isBlank()) {
            showError("Enter your email and password.");
            return;
        }
        setBusy(true);
        Thread worker = new Thread(() -> {
            AuthResult result = ServiceFactory.authService().login(email, password);
            Platform.runLater(() -> {
                setBusy(false);
                if (!result.success()) {
                    showError(result.message());
                    return;
                }
                ShellView.refreshSessionUi();
                ViewNavigator.show(ViewNavigator.DISCOVERY_VIEW);
            });
        }, "auth-login");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        loginButton.setText(busy ? "Signing in…" : "Log in");
        if (busy) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
