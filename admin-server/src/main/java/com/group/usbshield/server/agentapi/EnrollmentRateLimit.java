package com.group.usbshield.server.agentapi;

import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
final class EnrollmentRateLimit {
    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int MAX_REQUESTS = 240;
    private static final int MAX_TICKET_REQUESTS = 12;
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private long windowStart = System.nanoTime();
    private int count;

    synchronized boolean allow(UUID ticket) {
        long now = System.nanoTime();
        if (now - windowStart >= WINDOW_NANOS) {
            windowStart = now;
            count = 0;
            attempts.clear();
        }
        if (count >= MAX_REQUESTS) return false;
        count++;
        return attempts.merge(ticket, 1, Integer::sum) <= MAX_TICKET_REQUESTS;
    }
}
