package com.group.usbshield.agent.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LinuxActiveUserResolver {

    public record SessionCandidate(
            String sessionId,
            String username,
            Long uid,
            String sessionType,
            String seat,
            boolean active,
            boolean remote
    ) {}

    /**
     * Phân giải người dùng thực tế trên Ubuntu bằng loginctl theo thuật toán của Member 2.
     */
    public ActiveUserInfo resolveActiveUser() {
        try {
            List<String> sessionIds = getLocalSessionIds();
            if (sessionIds.isEmpty()) {
                log.warn("[ActiveUserResolver] No sessions found via loginctl -> fallback UNKNOWN");
                return buildUnknownUser("no-sessions-found");
            }

            List<SessionCandidate> activeLocalCandidates = new ArrayList<>();
            for (String sid : sessionIds) {
                SessionCandidate candidate = inspectSession(sid);
                if (candidate != null && candidate.active() && !candidate.remote()) {
                    activeLocalCandidates.add(candidate);
                }
            }

            return evaluateCandidates(activeLocalCandidates);
        } catch (Exception e) {
            log.error("[ActiveUserResolver] Error executing loginctl: {}", e.getMessage());
            return buildUnknownUser("error-executing-loginctl");
        }
    }

    /**
     * Logic giải quyết danh sách ứng viên (Tách riêng để dễ dàng viết Unit Test).
     */
    public ActiveUserInfo evaluateCandidates(List<SessionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return buildUnknownUser("no-active-local-session");
        }

        // Nếu chỉ có duy nhất 1 session active local
        if (candidates.size() == 1) {
            SessionCandidate single = candidates.get(0);
            return toActiveUserInfo(single);
        }

        // Nếu có nhiều hơn 1 session active: Lọc session đồ họa trên seat0 (wayland / x11)
        List<SessionCandidate> graphicalCandidates = new ArrayList<>();
        for (SessionCandidate c : candidates) {
            if ("seat0".equalsIgnoreCase(c.seat()) &&
                    ("wayland".equalsIgnoreCase(c.sessionType()) || "x11".equalsIgnoreCase(c.sessionType()))) {
                graphicalCandidates.add(c);
            }
        }

        if (graphicalCandidates.size() == 1) {
            return toActiveUserInfo(graphicalCandidates.get(0));
        }

        // Nếu vẫn không có hoặc có từ 2 session đồ họa trở lên -> Nhập nhằng (Ambiguous)
        log.warn("[ActiveUserResolver] Ambiguous active local sessions (count={}) -> Fail-safe UNKNOWN", candidates.size());
        return buildUnknownUser("ambiguous-active-local-sessions");
    }

    private List<String> getLocalSessionIds() throws Exception {
        List<String> sessionIds = new ArrayList<>();
        Process process = new ProcessBuilder("loginctl", "list-sessions", "--no-legend").start();
        boolean completed = process.waitFor(3, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return sessionIds;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String[] tokens = line.split("\\s+");
                    if (tokens.length > 0 && !tokens[0].isBlank()) {
                        sessionIds.add(tokens[0]);
                    }
                }
            }
        }
        return sessionIds;
    }

    private SessionCandidate inspectSession(String sessionId) {
        try {
            String username = querySessionProperty(sessionId, "Name");
            String uidStr = querySessionProperty(sessionId, "User");
            String activeStr = querySessionProperty(sessionId, "Active");
            String remoteStr = querySessionProperty(sessionId, "Remote");
            String seat = querySessionProperty(sessionId, "Seat");
            String type = querySessionProperty(sessionId, "Type");

            Long uid = null;
            try {
                uid = uidStr != null && !uidStr.isBlank() ? Long.parseLong(uidStr) : null;
            } catch (NumberFormatException ignored) {}

            boolean active = "yes".equalsIgnoreCase(activeStr);
            boolean remote = "yes".equalsIgnoreCase(remoteStr);

            return new SessionCandidate(sessionId, username, uid, type, seat, active, remote);
        } catch (Exception e) {
            log.debug("[ActiveUserResolver] Could not inspect session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    private String querySessionProperty(String sessionId, String propertyName) throws Exception {
        Process process = new ProcessBuilder("loginctl", "show-session", sessionId, "-p", propertyName, "--value").start();
        boolean completed = process.waitFor(2, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        }
    }

    private ActiveUserInfo toActiveUserInfo(SessionCandidate candidate) {
        return ActiveUserInfo.builder()
                .username(candidate.username() != null ? candidate.username() : "UNKNOWN")
                .uid(candidate.uid())
                .sessionId(candidate.sessionId())
                .sessionType(candidate.sessionType())
                .seat(candidate.seat())
                .build();
    }

    private ActiveUserInfo buildUnknownUser(String reason) {
        return ActiveUserInfo.builder()
                .username("UNKNOWN")
                .uid(null)
                .sessionId(null)
                .sessionType(null)
                .seat(null)
                .build();
    }
}
