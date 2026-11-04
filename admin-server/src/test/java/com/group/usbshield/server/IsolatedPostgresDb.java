package com.group.usbshield.server;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Dedicated, non-application database and unique schema per integration-test class.
 * Each class additionally calls TestPostgresSchemas.register() from @DynamicPropertySource.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(TestPostgresSchemaCleanup.class)
@TestPropertySource(properties = {
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "usbshield.enrollment-token=integration-test-bootstrap"
})
public @interface IsolatedPostgresDb {}
