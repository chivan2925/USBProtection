package com.group.usbshield.agent.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Bản giả lập phân giải người dùng đang hoạt động (dùng khi chưa có loginctl/D-Bus thật từ Member 2).
 * Khi Member 2 cung cấp LoginctlActiveUserResolver thật, Spring Boot sẽ ưu tiên bean đó.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "realActiveUserResolver")
public class MockActiveUserResolver implements ActiveUserResolver {

    @Override
    public ActiveUserInfo resolveCurrentActiveUser() {
        log.debug("[MOCK] Resolving active user from mock session");
        return ActiveUserInfo.builder()
                .username("student01")
                .uid(1001L)
                .sessionId("3")
                .sessionType("wayland")
                .seat("seat0")
                .build();
    }
}
