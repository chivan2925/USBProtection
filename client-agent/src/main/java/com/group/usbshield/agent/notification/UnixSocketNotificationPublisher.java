package com.group.usbshield.agent.notification;

import org.springframework.stereotype.Component;
import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/** Member 2 — Week 2: local-only per-UID socket client. M3 provisions directory/ACL. */
@Component
public class UnixSocketNotificationPublisher implements ClientNotificationPublisher {
    private final Path directory;
    public UnixSocketNotificationPublisher() {this(Path.of("/run/usbshield-notifier"));}
    public UnixSocketNotificationPublisher(Path directory) {this.directory=directory;}
    @Override public Delivery publish(UsbBlockNotification notice) {
        if(notice==null || notice.targetUid()<0)return Delivery.SKIPPED_NO_GUI;
        Path socket=directory.resolve(notice.targetUid()+".sock");
        for(int attempt=0;attempt<3;attempt++) {
            try {
                if(!Files.exists(socket))return Delivery.SKIPPED_NO_GUI;
                if(Files.isSymbolicLink(socket))return Delivery.FAILED;
                Object owner=Files.getAttribute(socket,"unix:uid");
                if(!(owner instanceof Number n) || n.longValue()!=notice.targetUid())return Delivery.FAILED;
                try(SocketChannel channel=SocketChannel.open(StandardProtocolFamily.UNIX)) {
                    channel.connect(UnixDomainSocketAddress.of(socket));
                    DataOutputStream output=new DataOutputStream(Channels.newOutputStream(channel));
                    output.writeLong(notice.targetUid());
                    output.writeUTF(notice.title());
                    output.writeUTF(notice.body());
                    output.flush();
                }
                return Delivery.DELIVERED; // handed to helper; not proof of visual display.
            } catch (IOException e) {
                // Socket inode may exist briefly before the desktop helper begins accepting.
                if(attempt==2)return Delivery.FAILED;
                try {Thread.sleep(30);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return Delivery.FAILED;}
            } catch (RuntimeException e) {return Delivery.FAILED;}
        }
        return Delivery.FAILED;
    }
}
