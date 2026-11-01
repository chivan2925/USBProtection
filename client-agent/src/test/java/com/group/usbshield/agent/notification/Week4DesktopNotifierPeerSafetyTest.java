package com.group.usbshield.agent.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Week4DesktopNotifierPeerSafetyTest {
    @Test
    void onlyUsbshieldServiceUserMayRequestDesktopNotification() {
        assertTrue(DesktopNotificationClient.authorizedAgentPeer("usbshield"));
        assertFalse(DesktopNotificationClient.authorizedAgentPeer("root"));
        assertFalse(DesktopNotificationClient.authorizedAgentPeer("student01"));
        assertFalse(DesktopNotificationClient.authorizedAgentPeer(null));
    }
}
