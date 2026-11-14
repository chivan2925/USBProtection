package com.group.usbshield.agent.notification;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Locale;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;
import jdk.net.ExtendedSocketOptions;
import jdk.net.UnixDomainPrincipal;

/**
 * M2's user-session notifier. M3 packages acl and XDG autostart. The user's
 * logind runtime directory prevents other UIDs from claiming this socket name;
 * the GUI user grants service user usbshield the minimum search/write ACL.
 * No root service or permissive shared 1777 directory is required.
 */
public class DesktopNotificationClient implements AutoCloseable {
    private final Path socketPath;
    private final NotifySendDesktopCommand command;
    private ServerSocketChannel server;

    public DesktopNotificationClient(Path socketPath, NotifySendDesktopCommand command) {
        this.socketPath = socketPath;
        this.command = command;
    }

    public void serve() throws IOException {
        long uid = ownUid();
        if (!socketPath.equals(NotifierSocketPaths.socketPath(
                NotifierSocketPaths.DEFAULT_RUNTIME_ROOT, uid))) {
            throw new SecurityException("Notifier socket path/UID mismatch");
        }
        Path runtime = socketPath.getParent().getParent();
        Path privateDir = socketPath.getParent();
        verifyOwnedDirectory(runtime, uid);
        if (Files.notExists(privateDir, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(privateDir,
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
            } catch (java.nio.file.FileAlreadyExistsException race) {
                throw new IOException("Notifier directory creation race", race);
            }
        }
        verifyOwnedDirectory(privateDir, uid);
        // Owner may grant this named ACL without belonging to the usbshield group.
        // Both directories allow only traversal (not listing) by the Agent.
        grantAgentAcl(runtime, "u:usbshield:--x");
        grantAgentAcl(privateDir, "u:usbshield:--x");
        // Serialize all compliant helpers, including recovery after a crash.
        // The lock lives in the UID-owned 0700 directory and is never accessible to the Agent.
        Path lockPath = privateDir.resolve("notice.lock");
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Notifier lock path is not a regular file");
        }
        try (FileChannel lockChannel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(lockPath)
                    || ((Number) Files.getAttribute(lockPath, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue() != uid) {
                throw new IOException("Notifier lock has unexpected owner or type");
            }
            Files.setPosixFilePermissions(lockPath, PosixFilePermissions.fromString("rw-------"));
            try (FileLock held = lockChannel.tryLock()) {
                if (held == null) throw new IOException("Another notifier helper holds the startup lock");
                recoverStaleSocket(socketPath, uid);
                serveBoundSocket(uid);
            }
        }
    }

    private void serveBoundSocket(long uid) throws IOException {
        Object boundFileKey = null;
        try (ServerSocketChannel live = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server = live;
            live.bind(UnixDomainSocketAddress.of(socketPath));
            boundFileKey = Files.readAttributes(socketPath, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).fileKey();
            Files.setPosixFilePermissions(socketPath,
                    PosixFilePermissions.fromString("rw-------"));
            grantAgentAcl(socketPath, "u:usbshield:rw-");
            while (live.isOpen()) {
                try (SocketChannel peer = live.accept()) {
                    UnixDomainPrincipal credentials;
                    try {
                        credentials = peer.getOption(ExtendedSocketOptions.SO_PEERCRED);
                    } catch (UnsupportedOperationException | SecurityException unavailable) {
                        continue;
                    }
                    if (credentials == null || credentials.user() == null
                            || !authorizedAgentPeer(credentials.user().getName())) {
                        continue;
                    }
                    DataInputStream input = new DataInputStream(Channels.newInputStream(peer));
                    long target = input.readLong();
                    String title = input.readUTF();
                    String body = input.readUTF();
                    if (target != uid || !"USBShield — USB Mass Storage bị chặn".equals(title)
                            || !"Thiết bị lưu trữ USB chưa được cấp phép. Vui lòng liên hệ quản trị viên.".equals(body)) {
                        continue;
                    }
                    command.execute(title, body);
                } catch (IOException failure) {
                    if (!live.isOpen()) break;
                }
            }
        } finally {
            server = null;
            // Only remove the inode bound by THIS helper, never a newer replacement.
            if (boundFileKey != null && Files.exists(socketPath, LinkOption.NOFOLLOW_LINKS)
                    && sameOwnedSocket(socketPath, uid, boundFileKey)) {
                Files.deleteIfExists(socketPath);
            }
        }
    }

    /** Fail-closed stale-socket recovery; requires the caller to hold notice.lock. */
    static void recoverStaleSocket(Path path, long uid) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Object originalKey = requireOwnedSocket(path, uid);
        try (SocketChannel probe = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            probe.connect(UnixDomainSocketAddress.of(path));
            // A listener accepted our connection: it is alive; NEVER unlink it.
            throw new IOException("Notifier socket is active; refusing to replace it");
        } catch (ConnectException refused) {
            // Only ENOTCONN/ECONNREFUSED represents an abandoned socket inode.
            // Permission denied, path errors and other failures must remain fatal.
            if (refused.getMessage() == null
                    || !refused.getMessage().toLowerCase(Locale.ROOT).contains("refused")) {
                throw refused;
            }
        }
        if (!sameOwnedSocket(path, uid, originalKey)) {
            throw new IOException("Notifier socket changed while probing; refusing unlink");
        }
        Files.delete(path);
    }

    private static Object requireOwnedSocket(Path path, long uid) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or symlink notifier socket; refusing unlink");
        }
        int mode = ((Number) Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS)).intValue();
        long owner = ((Number) Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue();
        if ((mode & 0170000) != 0140000 || owner != uid) {
            throw new IOException("Notifier path is not an owned Unix socket; refusing unlink");
        }
        Object key = Files.readAttributes(path, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
        if (key == null) throw new IOException("Cannot identify notifier socket inode");
        return key;
    }

    private static boolean sameOwnedSocket(Path path, long uid, Object key) throws IOException {
        return Objects.equals(requireOwnedSocket(path, uid), key);
    }

    private static long ownUid() throws IOException {
        try {
            return ((Number) Files.getAttribute(Path.of("/proc/self"), "unix:uid")).longValue();
        } catch (RuntimeException unexpected) {
            throw new IOException("Unable to inspect notifier UID", unexpected);
        }
    }

    private static void verifyOwnedDirectory(Path directory, long expectedUid) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new IOException("Notifier directory absent, symlink or non-directory: " + directory);
        }
        Object owner = Files.getAttribute(directory, "unix:uid", LinkOption.NOFOLLOW_LINKS);
        if (!(owner instanceof Number n) || n.longValue() != expectedUid) {
            throw new IOException("Notifier directory has unexpected owner: " + directory);
        }
        Object mode = Files.getAttribute(directory, "unix:mode", LinkOption.NOFOLLOW_LINKS);
        if (!(mode instanceof Number m)
                || !NotifierSocketPaths.protectedRuntimeDirectoryMode(m.intValue())) {
            throw new IOException("Notifier directory permissions too broad: " + directory);
        }
    }

    private static void grantAgentAcl(Path target, String entry) throws IOException {
        Path executable = Path.of("/usr/bin/setfacl");
        if (!Files.isExecutable(executable)) {
            throw new IOException("Missing acl package: /usr/bin/setfacl");
        }
        Process process = new ProcessBuilder(executable.toString(), "-m", entry,
                "--", target.toString()).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("ACL setup timed out for " + target);
            }
            if (process.exitValue() != 0) {
                throw new IOException("ACL setup failed for " + target + " (exit "
                        + process.exitValue() + ")");
            }
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Notifier ACL setup interrupted", interrupted);
        }
    }

    static boolean authorizedAgentPeer(String username) {
        return "usbshield".equals(username);
    }

    @Override public void close() throws IOException {
        if (server != null) server.close();
    }
}
