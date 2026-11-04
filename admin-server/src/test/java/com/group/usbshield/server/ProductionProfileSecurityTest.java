package com.group.usbshield.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("prod")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IsolatedPostgresDb
@SpringBootTest
class ProductionProfileSecurityTest {
    @Autowired Environment environment;
    @Autowired MockMvc mvc;
    @Test void deploymentDefaultsDoNotRelaxCookiesOrExposeConsole() {
        assertThat(environment.getProperty("server.servlet.session.cookie.secure",Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only",Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.jpa.database-platform")).isEqualTo("org.hibernate.dialect.PostgreSQLDialect");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("spring.jpa.open-in-view",Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.datasource.url")).contains("/usbshield_test");
        assertThat(environment.getProperty("springdoc.api-docs.enabled",Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled",Boolean.class)).isFalse();
    }

    @Test void productionDoesNotExposeInteractiveApiDocumentation() throws Exception {
        mvc.perform(get("/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
