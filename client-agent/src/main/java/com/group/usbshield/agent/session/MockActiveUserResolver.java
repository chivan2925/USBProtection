package com.group.usbshield.agent.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bản giả lập phân giải người dùng (dùng cho môi trường dev Windows).
 */
@Slf4j
@Component
public class MockActiveUserResolver implements ActiveUserResolver {

    @Override
    public ActiveUserInfo resolveCurrentActiveUser() {
        log.debug("[MOCK] Resolving active user from mock session (student01)");
        return ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0");
    }
}
