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
        return ActiveUserInfo.builder()
                .username("student01")
                .uid(1001L)
                .sessionId("3")
                .sessionType("wayland")
                .seat("seat0")
                .build();
    }
}
