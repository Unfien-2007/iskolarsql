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

/** Login screen (BCrypt via {@code AuthService}); Enter key submits. */
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
        AuthResult result = ServiceFactory.authService().login(emailField.getText(), passwordField.getText());
        if (!result.success()) {
            errorLabel.setText(result.message());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            return;
        }
        ShellView.refreshSessionUi();
        ViewNavigator.show(ViewNavigator.DISCOVERY_VIEW);
    }
}
