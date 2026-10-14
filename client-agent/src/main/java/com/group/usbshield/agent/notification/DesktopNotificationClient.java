package com.group.usbshield.agent.notification;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;

/** Member 2 — Week 2 user-session helper. M3 owns invocation, socket directory/ACL and autostart. */
public class DesktopNotificationClient implements AutoCloseable {
    private final Path socketPath;
    private final NotifySendDesktopCommand command;
    private ServerSocketChannel server;
    public DesktopNotificationClient(Path socketPath, NotifySendDesktopCommand command) {this.socketPath=socketPath;this.command=command;}
    public void serve() throws IOException {
        long uid;
        try { uid=((Number)Files.getAttribute(Path.of("/proc/self"),"unix:uid")).longValue(); }
        catch (RuntimeException e) {throw new IOException("Unable to inspect helper UID",e);}
        if(uid<0||!socketPath.getFileName().toString().equals(uid+".sock"))throw new SecurityException("Socket UID mismatched");
        // Parent path and permissions are provisioned by M3; do NOT chmod or create world-writable paths.
        if(!Files.isDirectory(socketPath.getParent()))throw new IOException("Notifier socket directory absent");
        if(Files.exists(socketPath))throw new IOException("Notifier socket already exists (do not unlink untrusted file)");
        try(ServerSocketChannel live=ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server=live; live.bind(UnixDomainSocketAddress.of(socketPath));
            try {Files.setPosixFilePermissions(socketPath,java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw----"));}
            catch(UnsupportedOperationException e) {throw new IOException("Unix permissions unavailable",e);}
            while(live.isOpen()) {
                try(SocketChannel peer=live.accept()) {
                    DataInputStream input=new DataInputStream(Channels.newInputStream(peer));
                    long target=input.readLong();String title=input.readUTF();String body=input.readUTF();
                    if(target!=uid || !"USBShield — USB Mass Storage bị chặn".equals(title)
                            || !"Thiết bị lưu trữ USB chưa được cấp phép. Vui lòng liên hệ quản trị viên.".equals(body))continue;
                    command.execute(title,body);
                } catch (IOException e) {if(!live.isOpen())break;}
            }
        } finally {server=null;Files.deleteIfExists(socketPath);}
    }
    @Override public void close() throws IOException {if(server!=null)server.close();}
}
