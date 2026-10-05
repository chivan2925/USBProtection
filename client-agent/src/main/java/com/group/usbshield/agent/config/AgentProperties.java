package com.group.usbshield.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "usbshield.agent")
public class AgentProperties {
    private String serverUrl = "http://localhost:8080";
    private String enrollmentToken = "default-enrollment-secret";
    private long heartbeatIntervalMs = 5000;
    private String dataDir = "/var/lib/usbshield-agent";
    private String configDir = "/etc/usbshield-agent";
}
