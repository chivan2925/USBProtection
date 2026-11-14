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
    /** Enforced by the live profile before any enrollment/token transmission. */
    private boolean requireHttps = false;
    /** Optional PEM CA file for a privately signed HTTPS server (normal hostname verification remains enabled). */
    private String tlsCaCertificate = "";
    private long heartbeatIntervalMs = 5000;
    private String dataDir = "/var/lib/usbshield-agent";
    private String configDir = "/etc/usbshield-agent";
    /** Live Ubuntu must never store credentials/audit under a relative working directory. */
    private boolean requireAbsoluteStorage = false;
    /** Maximum combined active + quarantined outbox footprint; reject, never evict, when full. */
    private long outboxMaxBytes = 64L * 1024 * 1024;
    /** Maximum active + quarantined event evidence files. */
    private int outboxMaxEvents = 10000;
    private boolean notificationEnabled = true;
    private boolean simulationEnabled = false;
}
