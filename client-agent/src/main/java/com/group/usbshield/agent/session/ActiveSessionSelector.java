package com.group.usbshield.agent.session;

import java.util.List;
import java.util.Objects;

final class ActiveSessionSelector {
    private ActiveSessionSelector() {
    }

    static ActiveUserInfo select(List<SessionProperties> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return ActiveUserInfo.unknown("no-active-local-session");
        }
        List<SessionProperties> active = sessions.stream()
                .filter(Objects::nonNull)
                .filter(SessionProperties::activeLocal)
                .toList();
        if (active.isEmpty()) {
            return ActiveUserInfo.unknown("no-active-local-session");
        }
        SessionProperties selected;
        if (active.size() == 1) {
            selected = active.getFirst();
        } else {
            List<SessionProperties> graphical = active.stream()
                    .filter(SessionProperties::graphicalSeat0)
                    .toList();
            if (graphical.size() != 1) {
                return ActiveUserInfo.unknown("ambiguous-active-local-sessions");
            }
            selected = graphical.getFirst();
        }
        if (selected.sessionId() == null || !selected.sessionId().matches("[A-Za-z0-9_-]{1,64}")
                || selected.name() == null || selected.name().isBlank()
                || "UNKNOWN".equalsIgnoreCase(selected.name())
                || selected.name().chars().anyMatch(Character::isISOControl)
                || selected.user() == null || !selected.user().matches("[0-9]+")) {
            return ActiveUserInfo.unknown("invalid-session-identity");
        }
        try {
            long uid = Long.parseLong(selected.user());
            return ActiveUserInfo.resolved(selected.name(), uid, selected.sessionId(),
                    selected.type(), selected.seat());
        } catch (NumberFormatException invalidUid) {
            return ActiveUserInfo.unknown("invalid-session-identity");
        }
    }
}
