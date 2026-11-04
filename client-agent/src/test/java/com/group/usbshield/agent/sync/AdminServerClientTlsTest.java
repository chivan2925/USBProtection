package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AdminServerClientTlsTest {
    @Test void liveRequiresExplicitHttpsOrigin() {
        var settings = new AgentProperties();
        settings.setRequireHttps(true);
        for (String url : new String[]{"http://localhost:8080", "http://usbshield.example:443", "",
                "https://name:secret@usbshield.example", "https://usbshield.example/path",
                "https://usbshield.example/?x=1", "ftp://usbshield.example"}) {
            settings.setServerUrl(url);
            assertThrows(IllegalStateException.class, () -> new AdminServerClient(settings), url);
        }
        settings.setServerUrl("https://usbshield.example");
        assertDoesNotThrow(() -> new AdminServerClient(settings));
    }

    @Test void invalidCustomCaFailsClosed() {
        var settings = new AgentProperties();
        settings.setServerUrl("https://usbshield.example");
        settings.setRequireHttps(true);
        settings.setTlsCaCertificate("relative/ca.pem");
        assertThrows(IllegalStateException.class, () -> new AdminServerClient(settings));
        settings.setTlsCaCertificate(Path.of("/this-ca-does-not-exist-USBShield.pem").toString());
        assertThrows(IllegalStateException.class, () -> new AdminServerClient(settings));
    }

    @Test void liveProfileCannotBeDowngradedByExternalProperty() {
        var settings = new AgentProperties();
        settings.setServerUrl("http://usbshield.example:8080");
        settings.setRequireHttps(false);
        var live = new org.springframework.mock.env.MockEnvironment();
        live.setActiveProfiles("live");
        assertThrows(IllegalStateException.class, () -> new AdminServerClient(settings, live));
    }

    @Test void developerHttpRemainsPossibleOnlyWithoutLiveRequirement() {
        var settings = new AgentProperties();
        settings.setRequireHttps(false);
        settings.setServerUrl("http://127.0.0.1:8080");
        assertDoesNotThrow(() -> new AdminServerClient(settings));
        settings.setRequireHttps(true);
        assertThrows(IllegalStateException.class, () -> new AdminServerClient(settings));
    }
}
