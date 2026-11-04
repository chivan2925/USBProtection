package com.group.usbshield.agent.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Linux inode/peer probes; no root, privileged ACL, or GUI session required. */
@EnabledOnOs(OS.LINUX)
class NotifierStaleSocketRecoveryTest {
    @TempDir Path dir;
    private long uid() throws Exception {
        return ((Number) Files.getAttribute(Path.of("/proc/self"), "unix:uid")).longValue();
    }
    @Test void staleSocketIsRecoveredAfterServerHasExited() throws Exception {
        Path socket = dir.resolve("notice.sock");
        try (ServerSocketChannel listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            listener.bind(UnixDomainSocketAddress.of(socket));
        }
        assertTrue(Files.exists(socket));
        DesktopNotificationClient.recoverStaleSocket(socket, uid());
        assertFalse(Files.exists(socket));
    }
    @Test void activeSocketIsNeverUnlinked() throws Exception {
        Path socket = dir.resolve("notice.sock");
        try (ServerSocketChannel listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            listener.bind(UnixDomainSocketAddress.of(socket));
            assertThrows(Exception.class, () -> DesktopNotificationClient.recoverStaleSocket(socket, uid()));
            assertTrue(Files.exists(socket));
        }
    }
    @Test void regularFileAndSymlinkAreNeverUnlinked() throws Exception {
        Path regular = dir.resolve("regular");
        Files.writeString(regular, "not a socket");
        assertThrows(Exception.class, () -> DesktopNotificationClient.recoverStaleSocket(regular, uid()));
        assertTrue(Files.exists(regular));
        Path link = dir.resolve("notice.sock");
        Files.createSymbolicLink(link, regular);
        assertThrows(Exception.class, () -> DesktopNotificationClient.recoverStaleSocket(link, uid()));
        assertTrue(Files.isSymbolicLink(link));
    }
}
