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
 * Self-registration: creates a STUDENT account on the central catalog server
 * (online-only) and signs the new student in right away.
 */
public class RegisterController {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button registerButton;
    @FXML
    private Hyperlink loginLink;

    @FXML
    private void initialize() {
        registerButton.setOnAction(e -> register());
        confirmField.setOnAction(e -> register());
        loginLink.setOnAction(e -> ViewNavigator.show(ViewNavigator.LOGIN_VIEW));
    }

    private void register() {
        if (!passwordField.getText().equals(confirmField.getText())) {
            showError("Passwords do not match.");
            return;
        }
        String email = emailField.getText();
        String password = passwordField.getText();
        setBusy(true);
        Thread worker = new Thread(() -> {
            AuthResult result = ServiceFactory.authService().register(email, password);
            Platform.runLater(() -> {
                setBusy(false);
                if (!result.success()) {
                    showError(result.message());
                    return;
                }
                ShellView.refreshSessionUi();
                ViewNavigator.show(ViewNavigator.DISCOVERY_VIEW);
            });
        }, "auth-register");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        registerButton.setDisable(busy);
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        confirmField.setDisable(busy);
        registerButton.setText(busy ? "Creating account…" : "Create account");
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
