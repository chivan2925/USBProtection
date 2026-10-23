package com.group.usbshield.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("prod")
@SpringBootTest(properties={"USBSHIELD_DB_URL=jdbc:h2:mem:prod-test;DB_CLOSE_DELAY=-1",
        "USBSHIELD_DB_PASSWORD=isolated-test-db-password", "USBSHIELD_ENROLLMENT_TOKEN=isolated-test-bootstrap"})
class Week3ProductionProfileTest {
    @Autowired Environment environment;
    @Test void deploymentDefaultsDoNotRelaxCookiesOrExposeConsole() {
        assertThat(environment.getProperty("server.servlet.session.cookie.secure",Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only",Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.h2.console.enabled",Boolean.class)).isFalse();
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("spring.jpa.open-in-view",Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.datasource.url")).contains("prod-test");
    }
}
