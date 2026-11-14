package com.group.usbshield.agent.notification;

import java.nio.file.Path;

/**
 * Cross-owner M2/M3 IPC contract (Week 4).
 * The login session owns its runtime directory; there is no shared writable
 * /run/usbshield-notifier namespace. The GUI helper provisions a private socket
 * and POSIX ACLs granting only the unprivileged Agent access.
 */
final class NotifierSocketPaths {
    static final String PRIVATE_DIRECTORY = "usbshield-notifier";
    static final String SOCKET_NAME = "notice.sock";
    static final long MAX_LINUX_UID = 4_294_967_295L;
    static final Path DEFAULT_RUNTIME_ROOT = Path.of("/run/user");

    private NotifierSocketPaths() {}

    static Path runtimeDirectory(Path runtimeRoot, long uid) {
        // Validate the exact Linux login-runtime root, rather than Path.isAbsolute().
        // On Windows, Path.of("/run/user") is drive-relative even though it names
        // the canonical Linux root used by our cross-platform contract tests.
        if (!DEFAULT_RUNTIME_ROOT.equals(runtimeRoot)
                || uid < 1000 || uid > MAX_LINUX_UID) {
            throw new IllegalArgumentException("Invalid GUI UID or runtime root");
        }
        return runtimeRoot.resolve(Long.toString(uid));
    }

    static Path socketPath(Path runtimeRoot, long uid) {
        return runtimeDirectory(runtimeRoot, uid)
                .resolve(PRIVATE_DIRECTORY).resolve(SOCKET_NAME);
    }

    static Path helperSocketPath(long uid, String xdgRuntimeDir) {
        runtimeDirectory(DEFAULT_RUNTIME_ROOT, uid); // validate UID bounds
        // XDG_RUNTIME_DIR is a Linux environment value and always uses '/'.
        // Do not compare it to Path.toString(), whose separators differ on Windows.
        String expectedLinuxPath = "/run/user/" + uid;
        if (!expectedLinuxPath.equals(xdgRuntimeDir)) {
            throw new IllegalArgumentException("XDG_RUNTIME_DIR must match this login UID");
        }
        return socketPath(DEFAULT_RUNTIME_ROOT, uid);
    }

    /** Reject directories writable by group/other or accessible to all users. */
    static boolean protectedRuntimeDirectoryMode(int unixMode) {
        return (unixMode & 0007) == 0 && (unixMode & 0020) == 0;
    }

    static boolean authorizedHelperPeer(String actualUsername, String ownerUsername) {
        return actualUsername != null && !actualUsername.isBlank()
                && actualUsername.equals(ownerUsername);
    }
}
