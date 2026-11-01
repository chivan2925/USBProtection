package com.group.usbshield.agent.notification;

import java.nio.file.Path;

/** Runs as the active graphical user, never as the privileged service. */
public final class DesktopNotificationClientMain {
    private DesktopNotificationClientMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("Expected own decimal GUI UID");
        }
        long uid = Long.parseLong(args[0]);
        Path socket = NotifierSocketPaths.helperSocketPath(uid, System.getenv("XDG_RUNTIME_DIR"));
        try (DesktopNotificationClient helper = new DesktopNotificationClient(
                socket, new NotifySendDesktopCommand())) {
            helper.serve();
        }
    }
}
