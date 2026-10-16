package com.group.usbshield.agent.notification;
import java.time.Instant;
/** Member 2 — Week 2: data-only notification (no authorization operation). */
public record UsbBlockNotification(long targetUid,String targetUsername,String deviceKey,String title,String body,Instant occurredAt) {}
