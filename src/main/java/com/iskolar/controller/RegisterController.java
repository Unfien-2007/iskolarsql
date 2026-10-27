package com.iskolar.controller;

import com.iskolar.service.AuthResult;
import com.iskolar.service.ServiceFactory;
import com.iskolar.util.ViewNavigator;
import com.iskolar.view.ShellView;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** Self-registration (always creates a STUDENT account, then auto-logs in). */
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
        AuthResult result = ServiceFactory.authService().register(emailField.getText(), passwordField.getText());
        if (!result.success()) {
            showError(result.message());
            return;
        }
        ShellView.refreshSessionUi();
        ViewNavigator.show(ViewNavigator.DISCOVERY_VIEW);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
