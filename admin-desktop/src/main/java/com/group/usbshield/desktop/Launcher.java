package com.group.usbshield.desktop;

import javafx.application.Application;

import javax.net.ssl.SSLException;

/** Separate main class for the native launcher and classpath packaging. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        if (args.length == 1 && "--check-central".equals(args[0])) {
            System.exit(checkCentral());
        }
        Application.launch(DesktopApplication.class, args);
    }

    private static int checkCentral() {
        CentralClient client;
        try {
            client = DesktopConfiguration.environment().connect();
        } catch (Exception invalid) {
            System.err.println("Check the Central loopback URL and the provisioned CA file.");
            return 2;
        }
        try (client) {
            client.checkReady();
            return 0;
        } catch (SSLException invalid) {
            System.err.println("Central TLS verification failed. Check CA, loopback IP SAN and system time.");
            return 2;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return 3;
        } catch (Exception unavailable) {
            System.err.println("Central HTTPS API is not ready.");
            return 3;
        }
    }
}
