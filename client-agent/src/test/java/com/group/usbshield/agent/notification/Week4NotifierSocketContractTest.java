package com.group.usbshield.agent.notification;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class Week4NotifierSocketContractTest {
    @Test void pathIsNamespacedUnderLogindUidNotWorldWritableDirectory() {
        assertEquals(Path.of("/run/user/1001/usbshield-notifier/notice.sock"),
                NotifierSocketPaths.socketPath(NotifierSocketPaths.DEFAULT_RUNTIME_ROOT, 1001));
    }

    @Test void arbitraryUidAndRuntimeRootAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> NotifierSocketPaths.socketPath(Path.of("tmp"), 1001));
        assertThrows(IllegalArgumentException.class, () -> NotifierSocketPaths.socketPath(Path.of("/tmp"), 1001));
        assertThrows(IllegalArgumentException.class, () -> NotifierSocketPaths.socketPath(Path.of("/run/user"), 0));
        assertThrows(IllegalArgumentException.class, () -> NotifierSocketPaths.socketPath(Path.of("/run/user"), -1));
        assertThrows(IllegalArgumentException.class, () -> NotifierSocketPaths.socketPath(Path.of("/run/user"), 4_294_967_296L));
    }

    @Test void helperDoesNotAcceptForgedXdgRuntimeDir() {
        assertEquals(Path.of("/run/user/1000/usbshield-notifier/notice.sock"),
                NotifierSocketPaths.helperSocketPath(1000, "/run/user/1000"));
        assertThrows(IllegalArgumentException.class,
                () -> NotifierSocketPaths.helperSocketPath(1000, "/tmp/fake"));
        assertThrows(IllegalArgumentException.class,
                () -> NotifierSocketPaths.helperSocketPath(1000, "/run/user/1000/../1001"));
        assertThrows(IllegalArgumentException.class,
                () -> NotifierSocketPaths.helperSocketPath(1000, null));
    }

    @Test void directoryPermissionsMustNotBeWorldAccessibleOrGroupWritable() {
        assertTrue(NotifierSocketPaths.protectedRuntimeDirectoryMode(0700));
        assertTrue(NotifierSocketPaths.protectedRuntimeDirectoryMode(0710)); // named ACL search mask
        assertFalse(NotifierSocketPaths.protectedRuntimeDirectoryMode(0777));
        assertFalse(NotifierSocketPaths.protectedRuntimeDirectoryMode(0720));
        assertFalse(NotifierSocketPaths.protectedRuntimeDirectoryMode(0701));
    }

    @Test void publisherMustRequireAuthenticatedHelperOwner() {
        assertTrue(NotifierSocketPaths.authorizedHelperPeer("student01", "student01"));
        assertFalse(NotifierSocketPaths.authorizedHelperPeer("student02", "student01"));
        assertFalse(NotifierSocketPaths.authorizedHelperPeer("root", "student01"));
        assertFalse(NotifierSocketPaths.authorizedHelperPeer(null, "student01"));
        assertFalse(NotifierSocketPaths.authorizedHelperPeer("", ""));
    }
}
