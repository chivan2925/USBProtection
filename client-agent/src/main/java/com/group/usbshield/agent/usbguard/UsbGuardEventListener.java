package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.*;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.*;
import com.group.usbshield.agent.system.SystemInfoProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Member 2 — Week 2: emit only PresenceChanged Insert/Remove; notification is fail-independent. */
@Component
public class UsbGuardEventListener {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(UsbGuardEventListener.class);
    private final UsbGuardEventParser eventParser;
    private final ActiveUserResolver activeUserResolver;
    private final EndpointManager endpointManager;
    private final SystemInfoProvider systemInfoProvider;
    private final EventSenderService eventSenderService;
    private final DesktopNotificationService desktopNotificationService;
    public UsbGuardEventListener(UsbGuardEventParser eventParser, ActiveUserResolver activeUserResolver,
            EndpointManager endpointManager, SystemInfoProvider systemInfoProvider,
            EventSenderService eventSenderService, DesktopNotificationService desktopNotificationService) {
        this.eventParser=eventParser;this.presenceParser=new UsbGuardPresenceParser(eventParser);this.activeUserResolver=activeUserResolver;
        this.endpointManager=endpointManager;this.systemInfoProvider=systemInfoProvider;
        this.eventSenderService=eventSenderService;this.desktopNotificationService=desktopNotificationService;
    }
    public enum WatchSection {NONE,PRESENCE_CHANGED,POLICY_CHANGED,POLICY_APPLIED}
    private WatchSection section=WatchSection.NONE;
    private final StringBuilder sectionLines=new StringBuilder();
    private final UsbGuardPresenceParser presenceParser;
    private final Map<String,Long> recent=new ConcurrentHashMap<>();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile boolean running=true;
    private volatile Process process;
    @PostConstruct public void startListening() {
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("linux"))return;
        executor.submit(this::watchLoop);
    }
    private void watchLoop() {
        while(running) {
            try {
                process=new ProcessBuilder("usbguard","watch").redirectErrorStream(true).start();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) {
                    String line;
                    while(running&&(line=reader.readLine())!=null) processWatchLine(line);
                }
                if(running) log.warn("USBGuard watch exited; retrying");
            } catch(IOException e) {if(running)log.warn("USBGuard watch IPC failed: {}",e.getMessage());}
            if(!running)break;
            try {TimeUnit.SECONDS.sleep(5);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
    }
    /** Receives individual raw watch lines. Only a complete PresenceChanged device_rule emits telemetry. */
    public synchronized void processWatchLine(String line) {
        if(line==null)return;
        String s=line.trim();
        if(s.isEmpty()||s.startsWith("[IPC]"))return;
        if(s.startsWith("[device] ")) {
            section=s.startsWith("[device] PresenceChanged:")?WatchSection.PRESENCE_CHANGED:
                    s.startsWith("[device] PolicyChanged:")?WatchSection.POLICY_CHANGED:
                    s.startsWith("[device] PolicyApplied:")?WatchSection.POLICY_APPLIED:WatchSection.NONE;
            sectionLines.setLength(0);
        }
        if(section==WatchSection.NONE)return;
        sectionLines.append(s).append('\n');
        if(s.startsWith("device_rule=") && section==WatchSection.PRESENCE_CHANGED) {
            presenceParser.parse(sectionLines.toString()).ifPresent(e -> {
                String wireType=e.presenceType()==UsbGuardPresenceEvent.PresenceType.INSERT?"CONNECTED":"DISCONNECTED";
                handle(e.device(),wireType,e.target());
            });
            section=WatchSection.NONE; // subsequent PolicyChanged/PolicyApplied never emit
            sectionLines.setLength(0);
        }
    }
    /** Backward-compatible unit test entry point; production uses processWatchLine. */
    public void handleDeviceRuleEvent(String raw,String eventType) {
        UsbDeviceInfo device=eventParser.parseDeviceLine(raw);
        if(device!=null && ("CONNECTED".equals(eventType)||"DISCONNECTED".equals(eventType)))
            handle(device,eventType,device.state());
    }
    private void handle(UsbDeviceInfo device,String type,String target) {
        String identity=device.hash()!=null&&!device.hash().isBlank()?device.hash():device.vendorId()+":"+device.productId()+":"+device.serial();
        String key=identity+":"+device.runtimeId()+":"+type;
        long now=System.currentTimeMillis();
        Long previous=recent.put(key,now);
        if(previous!=null&&now-previous<1500)return;
        if(recent.size()>1024)recent.entrySet().removeIf(e->now-e.getValue()>3000);
        UsbGuardPresenceEvent event=new UsbGuardPresenceEvent("CONNECTED".equals(type)?UsbGuardPresenceEvent.PresenceType.INSERT:
                UsbGuardPresenceEvent.PresenceType.REMOVE,target,device);
        ClientUsbEvent enriched=new UsbEventEnricher(activeUserResolver,Clock.systemUTC())
                .enrich(endpointManager.getEndpointId(),systemInfoProvider.getHostname(),event);
        UsbEventPayload wire=UsbEventPayload.builder().endpointId(enriched.endpointId()).hostname(enriched.hostname())
                .linuxUsername(enriched.linuxUsername()).linuxUid(enriched.linuxUid())
                .sessionId(enriched.sessionId()).sessionType(enriched.sessionType()).seat(enriched.seat())
                .device(enriched.device()).eventType(enriched.eventType()).decision(enriched.decision())
                .occurredAt(enriched.occurredAt()).build();
        if(event.presenceType()==UsbGuardPresenceEvent.PresenceType.INSERT && device.massStorage() &&
                ("block".equalsIgnoreCase(target)||"reject".equalsIgnoreCase(target))) {
            try {
                ActiveUserInfo user=new ActiveUserInfo(enriched.linuxUsername(),enriched.linuxUid(),enriched.sessionId(),
                        enriched.sessionType(),enriched.seat(),enriched.linuxUid()==null?"UNKNOWN":"RESOLVED",null);
                desktopNotificationService.notifyBlockedUsb(user,device);
            } catch(Exception e) {log.warn("Notification failed independently of event transport: {}",e.getMessage());}
        }
        try {eventSenderService.sendPayload(wire);}catch(Exception e){log.error("Event transport failed: {}",e.getMessage());}
    }
    @PreDestroy public void stopListening() {
        running=false;
        if(process!=null&&process.isAlive())process.destroy();
        executor.shutdownNow();
    }
}
