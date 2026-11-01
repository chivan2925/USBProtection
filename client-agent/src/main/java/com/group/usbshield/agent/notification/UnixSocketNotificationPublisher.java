package com.group.usbshield.agent.notification;

import org.springframework.stereotype.Component;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import jdk.net.ExtendedSocketOptions;
import jdk.net.UnixDomainPrincipal;

/**
 * Agent-side local IPC. The target helper's socket is under the target UID's
 * logind-owned runtime directory. Both file ownership and SO_PEERCRED must
 * match that UID before any notification payload is transmitted.
 */
@Component
public class UnixSocketNotificationPublisher implements ClientNotificationPublisher {
    private static final int UNIX_SOCKET_FILE_TYPE = 0140000;
    private static final int UNIX_FILE_TYPE_MASK = 0170000;
    private final Path runtimeRoot;

    public UnixSocketNotificationPublisher() {
        this(NotifierSocketPaths.DEFAULT_RUNTIME_ROOT);
    }

    /** Injection point for tests; production always uses /run/user. */
    public UnixSocketNotificationPublisher(Path runtimeRoot) {
        this.runtimeRoot = runtimeRoot;
    }

    @Override public Delivery publish(UsbBlockNotification notice) {
        if (notice == null || notice.targetUid() < 1000) return Delivery.SKIPPED_NO_GUI;
        final Path runtime;
        final Path privateDir;
        final Path socket;
        try {
            runtime = NotifierSocketPaths.runtimeDirectory(runtimeRoot, notice.targetUid());
            socket = NotifierSocketPaths.socketPath(runtimeRoot, notice.targetUid());
            privateDir = socket.getParent();
        } catch (RuntimeException invalid) {
            return Delivery.FAILED;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (!Files.exists(runtime, LinkOption.NOFOLLOW_LINKS)
                        || !Files.exists(privateDir, LinkOption.NOFOLLOW_LINKS)
                        || !Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) {
                    return Delivery.SKIPPED_NO_GUI;
                }
                if (!ownedDirectory(runtime, notice.targetUid())
                        || !ownedDirectory(privateDir, notice.targetUid())
                        || Files.isSymbolicLink(socket)
                        || !ownedByUid(socket, notice.targetUid())) {
                    return Delivery.FAILED;
                }
                Object mode = Files.getAttribute(socket, "unix:mode", LinkOption.NOFOLLOW_LINKS);
                if (!(mode instanceof Number n)
                        || (n.intValue() & UNIX_FILE_TYPE_MASK) != UNIX_SOCKET_FILE_TYPE) {
                    return Delivery.FAILED;
                }
                String expectedOwner = Files.getOwner(privateDir,
                        LinkOption.NOFOLLOW_LINKS).getName();
                try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                    channel.connect(UnixDomainSocketAddress.of(socket));
                    UnixDomainPrincipal peer = channel.getOption(ExtendedSocketOptions.SO_PEERCRED);
                    if (peer == null || peer.user() == null
                            || !NotifierSocketPaths.authorizedHelperPeer(
                                    peer.user().getName(), expectedOwner)) {
                        return Delivery.FAILED;
                    }
                    DataOutputStream output = new DataOutputStream(Channels.newOutputStream(channel));
                    output.writeLong(notice.targetUid());
                    output.writeUTF(notice.title());
                    output.writeUTF(notice.body());
                    output.flush();
                }
                return Delivery.DELIVERED; // Handoff only; not visual popup proof.
            } catch (IOException | UnsupportedOperationException | SecurityException failure) {
                if (attempt == 2) return Delivery.FAILED;
                try {Thread.sleep(30);} catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return Delivery.FAILED;
                }
            }
        }
        return Delivery.FAILED;
    }

    private static boolean ownedDirectory(Path path, long uid) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path) || !ownedByUid(path, uid)) return false;
        Object mode = Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS);
        return mode instanceof Number n
                && NotifierSocketPaths.protectedRuntimeDirectoryMode(n.intValue());
    }

    private static boolean ownedByUid(Path path, long uid) throws IOException {
        Object owner = Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS);
        return owner instanceof Number n && n.longValue() == uid;
    }
}
