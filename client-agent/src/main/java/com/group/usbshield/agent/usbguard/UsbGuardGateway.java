package com.group.usbshield.agent.usbguard;

import java.util.List;

/**
 * Interface cho USBGuard Core (do Member 2 phụ trách hiện thực).
 * Member 3 gọi interface này để nạp Whitelist hoặc kiểm tra trạng thái USBGuard.
 */
public interface UsbGuardGateway {
    boolean isAvailable();
    void applyWhitelist(List<String> whitelistFingerprints);
}
