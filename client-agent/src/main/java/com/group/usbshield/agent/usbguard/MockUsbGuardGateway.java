package com.group.usbshield.agent.usbguard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bản giả lập USBGuard Gateway (dùng khi chưa có adapter USBGuard thật từ Member 2).
 * Khi Member 2 cung cấp UsbGuardCliGateway thật, Spring Boot sẽ ưu tiên bean đó.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "realUsbGuardGateway")
public class MockUsbGuardGateway implements UsbGuardGateway {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void applyWhitelist(List<String> whitelistFingerprints) {
        log.info("[MOCK-USBGUARD] Successfully applied {} whitelist rules to USBGuard engine:", whitelistFingerprints.size());
        for (String rule : whitelistFingerprints) {
            log.info("[MOCK-USBGUARD]   --> allow device fingerprint: {}", rule);
        }
    }
}
