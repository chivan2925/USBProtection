package com.group.usbshield.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public final class DesktopApplication extends Application {
    private DesktopController controller;

    @Override
    public void start(Stage stage) throws Exception {
        CentralClient client;
        try {
            client = DesktopConfiguration.environment().connect();
        } catch (Exception failure) {
            var error = new Alert(Alert.AlertType.ERROR, """
                    Kiểm tra USBSHIELD_DESKTOP_CA và USBSHIELD_CENTRAL_URL.
                    Central Desktop yêu cầu CA hợp lệ và HTTPS trên 127.0.0.1.
                    """.strip());
            error.setHeaderText("Không thể đọc cấu hình Central");
            error.showAndWait();
            return;
        }

        controller = new DesktopController(client);
        var loader = new FXMLLoader(DesktopApplication.class.getResource("desktop.fxml"));
        loader.setController(controller);
        var scene = new Scene(loader.load(), 1180, 760);
        scene.getStylesheets().add(DesktopApplication.class.getResource("desktop.css").toExternalForm());
        stage.setTitle("USBShield — Quản trị trung tâm");
        stage.setMinWidth(970);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) controller.close();
    }
}
