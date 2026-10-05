package com.group.usbshield.agent.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
public class SystemInfoProvider {

    public String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    public String getIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public String getMachineId() {
        Path machineIdPath = Path.of("/etc/machine-id");
        try {
            if (Files.exists(machineIdPath)) {
                return Files.readString(machineIdPath).trim();
            }
        } catch (Exception e) {
            log.warn("Could not read /etc/machine-id: {}", e.getMessage());
        }
        return UUID.randomUUID().toString();
    }
}
