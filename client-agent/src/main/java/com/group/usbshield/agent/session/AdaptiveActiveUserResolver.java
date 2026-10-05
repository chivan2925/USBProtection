package com.group.usbshield.agent.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Bộ phân giải thông minh:
 * - Khi chạy trên Linux: Gọi LinuxActiveUserResolver (thực thi loginctl thật).
 * - Khi chạy trên Windows (môi trường dev): Tự động fallback về Mock để ứng dụng chạy không bị lỗi.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class AdaptiveActiveUserResolver implements ActiveUserResolver {

    private final LinuxActiveUserResolver linuxResolver;
    private final MockActiveUserResolver mockResolver;

    @Override
    public ActiveUserInfo resolveCurrentActiveUser() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            return linuxResolver.resolveActiveUser();
        } else {
            log.info("[ActiveUserResolver] Non-Linux environment detected ('{}') -> Using mock active user", os);
            return mockResolver.resolveCurrentActiveUser();
        }
    }
}
