package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsbGuardWeek4WatchStressTest {
    private UsbGuardEventListener listener;
    private UsbGuardEventParser parser;
    private ActiveUserResolver resolver;
    private EndpointManager endpoint;
    private SystemInfoProvider system;
    private EventSenderService sender;
    private DesktopNotificationService notifier;

    @BeforeEach
    void setUp() {
        parser = new UsbGuardEventParser();
        resolver = mock(ActiveUserResolver.class);
        endpoint = mock(EndpointManager.class);
        system = mock(SystemInfoProvider.class);
        sender = mock(EventSenderService.class);
        notifier = mock(DesktopNotificationService.class);
        when(endpoint.getEndpointId()).thenReturn("EP-W4-1");
        when(system.getHostname()).thenReturn("lab-ubuntu");
        when(resolver.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0"));
        listener = create(null, 0);
    }

    @AfterEach
    void tearDown() {
        listener.stopListening();
    }

    private UsbGuardEventListener create(UsbGuardEventListener.WatchProcessLauncher launcher, long delay) {
        if (launcher == null) {
            return new UsbGuardEventListener(parser, resolver, endpoint, system, sender, notifier);
        }
        return new UsbGuardEventListener(parser, resolver, endpoint, system, sender, notifier, launcher, delay);
    }

    private void presence(int id, String action) {
        listener.processWatchLine("[device] PresenceChanged: id=" + id);
        listener.processWatchLine("event=" + action);
        listener.processWatchLine("target=block");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"hash-" + id + "\" with-interface 08:06:50");
    }

    private static String streamEvent(int id, String action) {
        return "[device] PresenceChanged: id=" + id + "\n"
                + "event=" + action + "\n"
                + "target=block\n"
                + "device_rule=block id 0951:1665 hash \"hash-" + id + "\" with-interface 08:06:50\n";
    }

    @Test
    void replayedInsertImmediatelyAfterReconnectIsDeduplicated() {
        presence(11, "Insert");
        listener.resetWatchState();
        presence(11, "Insert");
        verify(sender, times(1)).sendPayload(any());
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }

    @Test
    void removeAndReinsertRemainSeparateAfterReconnect() {
        presence(12, "Insert");
        listener.resetWatchState();
        presence(12, "Remove");
        listener.resetWatchState();
        presence(12, "Insert");
        verify(sender, times(3)).sendPayload(any());
        verify(notifier, times(2)).notifyBlockedUsb(any(), any());
        verify(notifier).onUsbRemoved(any(), any());
    }

    @Test
    void repeatedPresenceAndPolicyChangesDoNotDuplicateTransport() {
        for (int i = 0; i < 100; i++) {
            presence(13, "Insert");
            listener.processWatchLine("[device] PolicyChanged: id=13");
            listener.processWatchLine("device_rule=block id 0951:1665 hash \"hash-13\" with-interface 08:06:50");
            listener.processWatchLine("[device] PolicyApplied: id=13");
            listener.processWatchLine("device_rule=block id 0951:1665 hash \"hash-13\" with-interface 08:06:50");
        }
        verify(sender, times(1)).sendPayload(any());
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }

    @Test
    void malformedAndOversizedSectionCannotPoisonNextEvent() {
        listener.processWatchLine("[device] PresenceChanged: id=14");
        listener.processWatchLine("event=Insert");
        listener.processWatchLine("event=Insert");
        listener.processWatchLine("target=block");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"hash-14\" with-interface 08:06:50");
        listener.processWatchLine("[device] PresenceChanged: id=14");
        listener.processWatchLine("event=Insert");
        listener.processWatchLine("X".repeat(40000));
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"hash-14\" with-interface 08:06:50");
        verifyNoInteractions(sender);
        presence(14, "Insert");
        verify(sender).sendPayload(any());
    }

    @Test
    void boundedDedupStateUnderLargeNumberOfDifferentDevices() {
        for (int i = 0; i < 1100; i++) {
            presence(1000 + i, "Insert");
        }
        verify(sender, times(1100)).sendPayload(any());
        assertTrue(listener.trackedRecentEventCount() <= 1024);
    }

    @Test
    void boundedLineReaderRejectsOversizedInputBeforeFullLine() {
        BufferedReader input = new BufferedReader(new StringReader("X".repeat(40000) + "\n"));
        assertThrows(IOException.class, () -> UsbGuardEventListener.readWatchLine(input));
    }

    @Test
    void boundedLineReaderAcceptsNormalCrLfAndEndOfFile() throws Exception {
        BufferedReader input = new BufferedReader(new StringReader("event=Insert\r\nend"));
        assertEquals("event=Insert\r", UsbGuardEventListener.readWatchLine(input));
        assertEquals("end", UsbGuardEventListener.readWatchLine(input));
        assertNull(UsbGuardEventListener.readWatchLine(input));
    }

    @Test
    void daemonUnavailableThenReconnectEmitsEvent() {
        AtomicInteger attempts = new AtomicInteger();
        UsbGuardEventListener.WatchProcessLauncher launcher = () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("USBGuard daemon unavailable");
            }
            if (attempts.get() == 2) {
                return fakeProcess(streamEvent(41, "Insert"));
            }
            throw new IOException("USBGuard daemon unavailable again");
        };
        listener.stopListening();
        listener = create(launcher, 10);
        listener.startWatchWorker();
        verify(sender, timeout(2000).times(1)).sendPayload(any());
        assertTrue(attempts.get() >= 2);
    }

    @Test
    void replayedWatchOutputAcrossTwoProcessRestartsIsSuppressed() throws Exception {
        AtomicInteger launches = new AtomicInteger();
        CountDownLatch replayConsumed = new CountDownLatch(1);
        UsbGuardEventListener.WatchProcessLauncher launcher = () -> {
            int number = launches.incrementAndGet();
            if (number == 1) {
                return fakeProcess(streamEvent(77, "Insert"));
            }
            if (number == 2) {
                return fakeProcess(streamEvent(77, "Insert"), replayConsumed);
            }
            throw new IOException("USBGuard unavailable");
        };
        listener.stopListening();
        listener = create(launcher, 10);
        listener.startWatchWorker();
        assertTrue(replayConsumed.await(3, TimeUnit.SECONDS));
        verify(sender, times(1)).sendPayload(any());
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }

    private static Process fakeProcess(String content) {
        return fakeProcess(content, null);
    }

    private static Process fakeProcess(String content, CountDownLatch consumed) {
        InputStream source = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public synchronized int read(byte[] buffer, int off, int len) {
                int count = super.read(buffer, off, len);
                if (count == -1 && consumed != null) {
                    consumed.countDown();
                }
                return count;
            }
            @Override
            public synchronized int read() {
                int value = super.read();
                if (value == -1 && consumed != null) {
                    consumed.countDown();
                }
                return value;
            }
        };
        return new Process() {
            private final InputStream input = source;
            @Override
            public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override
            public InputStream getInputStream() { return input; }
            @Override
            public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            @Override
            public int waitFor() { return 0; }
            @Override
            public int exitValue() { return 0; }
            @Override
            public void destroy() { }
            @Override
            public boolean isAlive() { return false; }
        };
    }
}
