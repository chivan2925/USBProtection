package com.group.usbshield.agent.notification;

import java.io.DataInputStream;
import java.io.IOException;
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
        if (Files.exists(socketPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Notifier socket already exists; refusing unsafe unlink");
        }
        try (ServerSocketChannel live = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server = live;
            live.bind(UnixDomainSocketAddress.of(socketPath));
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
            // Never remove a symlink or replacement placed by another process.
            if (Files.exists(socketPath, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(socketPath)
                    && ((Number) Files.getAttribute(socketPath, "unix:uid",
                    LinkOption.NOFOLLOW_LINKS)).longValue() == uid) {
                Files.deleteIfExists(socketPath);
            }
        }
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
