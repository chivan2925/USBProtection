package com.group.usbshield.agent.notification;

import java.nio.file.Path;

/** Member 2 — Week 2 helper entry point. M3 installs and auto-starts it as the desktop user. */
public final class DesktopNotificationClientMain {
    private DesktopNotificationClientMain() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=1||!args[0].matches("[0-9]{1,10}"))throw new IllegalArgumentException("Expected own numeric UID");
        long uid=Long.parseLong(args[0]);
        Path socket=Path.of("/run/usbshield-notifier",uid+".sock");
        try(DesktopNotificationClient helper=new DesktopNotificationClient(socket,new NotifySendDesktopCommand())) {helper.serve();}
    }
}
