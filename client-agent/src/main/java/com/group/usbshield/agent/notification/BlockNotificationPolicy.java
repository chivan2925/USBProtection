package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.usbguard.*;
import com.group.usbshield.agent.session.ActiveUserInfo;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Member 2 — Week 2: pure eligibility + bounded cooldown for notification storms. */
public class BlockNotificationPolicy {
    private final ConcurrentHashMap<String,Long> recent = new ConcurrentHashMap<>();
    private final long cooldownMillis;
    public BlockNotificationPolicy() {this(1500);}
    public BlockNotificationPolicy(long cooldownMillis) {this.cooldownMillis=cooldownMillis;}
    public Optional<UsbBlockNotification> classify(UsbGuardPresenceEvent event,ActiveUserInfo user,Instant when) {
        if(event==null || user==null || when==null || event.device()==null ||
                event.presenceType()!=UsbGuardPresenceEvent.PresenceType.INSERT || !event.device().massStorage())return Optional.empty();
        if(!"block".equalsIgnoreCase(event.target())&&!"reject".equalsIgnoreCase(event.target()))return Optional.empty();
        if(!"RESOLVED".equals(user.status())||user.uid()==null||user.uid()<0||
                user.sessionId()==null||user.sessionId().isBlank()||
                (!"wayland".equalsIgnoreCase(user.sessionType())&&!"x11".equalsIgnoreCase(user.sessionType())))return Optional.empty();
        String deviceKey=event.device().hash();
        if(deviceKey==null||deviceKey.isBlank())deviceKey=event.device().vendorId()+":"+event.device().productId()+":"+event.device().serial();
        String key=user.uid()+":"+deviceKey;
        long now=when.toEpochMilli();
        if(cooldownMillis>0) {
            Long last=recent.put(key,now);
            if(last!=null&&now>=last&&now-last<cooldownMillis)return Optional.empty();
            if(recent.size()>1024)recent.entrySet().removeIf(e->now-e.getValue()>cooldownMillis*2);
        }
        String title="USBShield — USB Mass Storage bị chặn";
        String body="Thiết bị lưu trữ USB chưa được cấp phép. Vui lòng liên hệ quản trị viên.";
        return Optional.of(new UsbBlockNotification(user.uid(),user.username(),deviceKey,title,body,when));
    }
}
