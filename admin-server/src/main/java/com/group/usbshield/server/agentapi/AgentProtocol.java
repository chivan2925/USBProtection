package com.group.usbshield.server.agentapi;

import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UPGRADE_REQUIRED;

public final class AgentProtocol {
    public static final String HEADER = "X-USBShield-Protocol";

    private AgentProtocol() {}

    public static void requireCurrent(String value) {
        if (!"2".equals(value)) throw new ResponseStatusException(UPGRADE_REQUIRED, "USBShield Agent protocol 2 required");
    }
}
