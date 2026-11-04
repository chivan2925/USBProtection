package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** R06: a blocked USBGuard list-devices IPC must not block watch reconnect. */
class UsbGuardWatchReconnectReliabilityTest {
    @Test void watchdogReconnectsWhileIpcRecoveryIsBlocked() throws Exception {
        var gateway = mock(UsbGuardGateway.class);
        var endpoint = mock(EndpointManager.class);
        var system = mock(SystemInfoProvider.class);
        var users = mock(ActiveUserResolver.class);
        when(endpoint.getEndpointId()).thenReturn("EP-A");
        when(system.getHostname()).thenReturn("ubuntu-a");
        when(users.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("student", 1000L, "2", "wayland", "seat0"));
        CountDownLatch ipcStarted = new CountDownLatch(1);
        CountDownLatch allowIpc = new CountDownLatch(1);
        CountDownLatch ipcFinished = new CountDownLatch(1);
        CountDownLatch secondWatch = new CountDownLatch(1);
        when(gateway.listDevices()).thenAnswer(call -> {
            ipcStarted.countDown();
            try {
                if (!allowIpc.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                return List.of();
            } finally {
                ipcFinished.countDown();
            }
        });
        String insertion = "[device] PresenceChanged: id=11\n"
                + "event=Insert\ntarget=block\n"
                + "device_rule=block id 0951:1665 hash \"HashA=\" with-interface 08:06:50\n";
        PipedInputStream keepOpen = new PipedInputStream();
        try (PipedOutputStream writer = new PipedOutputStream(keepOpen)) {
            AtomicInteger watches = new AtomicInteger();
            UsbGuardEventListener.WatchProcessLauncher launcher = () -> {
                if (watches.incrementAndGet() == 1) {
                    return process(new ByteArrayInputStream(insertion.getBytes(StandardCharsets.UTF_8)));
                }
                secondWatch.countDown();
                return process(keepOpen);
            };
            var listener = new UsbGuardEventListener(new UsbGuardEventParser(), users,
                    endpoint, system, mock(EventSenderService.class),
                    mock(DesktopNotificationService.class), gateway, launcher, 0);
            try {
                listener.startWatchWorker();
                assertTrue(ipcStarted.await(3, TimeUnit.SECONDS), "IPC recovery never started");
                assertTrue(secondWatch.await(2, TimeUnit.SECONDS),
                        "Watch reconnect was blocked waiting for list-devices IPC");
                assertTrue(watches.get() >= 2);
            } finally {
                allowIpc.countDown();
                // Let the mocked blocking IPC complete before stopListening interrupts
                // the recovery executor; otherwise the test prints an uncaught
                // InterruptedException even though its assertions all passed.
                try {
                    ipcFinished.await(2, TimeUnit.SECONDS);
                } finally {
                    listener.stopListening();
                }
            }
        }
    }

    private static Process process(InputStream source) {
        return new Process() {
            @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
            @Override public InputStream getInputStream() { return source; }
            @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            @Override public int waitFor() { return 0; }
            @Override public int exitValue() { return 0; }
            @Override public void destroy() { try { source.close(); } catch (Exception ignored) {} }
            @Override public boolean isAlive() { return false; }
        };
    }
}
