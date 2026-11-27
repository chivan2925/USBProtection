package com.group.usbshield.desktop;

import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import static com.group.usbshield.desktop.CentralData.CredentialChange;

public final class AccountSettingsController {
    @FXML private Label currentUsername;
    @FXML private TextField newUsername;
    @FXML private PasswordField currentPassword;
    @FXML private PasswordField newPassword;
    @FXML private PasswordField confirmPassword;

    private Consumer<CredentialChange> save;
    private Consumer<String> showError;

    void configure(Consumer<CredentialChange> save, Consumer<String> showError) {
        this.save = save;
        this.showError = showError;
    }

    void open(String username) {
        clear();
        currentUsername.setText(username);
    }

    void clear() {
        currentUsername.setText("");
        newUsername.clear();
        clearPasswords();
    }

    private void clearPasswords() {
        currentPassword.clear();
        newPassword.clear();
        confirmPassword.clear();
    }

    @FXML private void submit() {
        String username = newUsername.getText().strip();
        String current = currentPassword.getText();
        String replacement = newPassword.getText();
        if (current.isBlank()) {
            showError.accept("Nhập mật khẩu hiện tại để xác nhận.");
        } else if ((username.isEmpty() || username.equals(currentUsername.getText())) && replacement.isEmpty()) {
            showError.accept("Nhập tên đăng nhập mới hoặc mật khẩu mới.");
        } else if (!replacement.equals(confirmPassword.getText())) {
            showError.accept("Hai mật khẩu mới chưa trùng khớp.");
        } else {
            clearPasswords();
            save.accept(new CredentialChange(current, username.isEmpty() ? null : username,
                    replacement.isEmpty() ? null : replacement));
        }
    }
}
