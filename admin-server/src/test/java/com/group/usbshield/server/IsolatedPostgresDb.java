package com.group.usbshield.server;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Forces destructive integration suites onto a fixed disposable database.
 * Even when SPRING_DATASOURCE_URL is set, tests cannot target application data.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://${USBSHIELD_TEST_DB_HOST:127.0.0.1}:${USBSHIELD_TEST_DB_PORT:5432}/usbshield_test",
    "spring.datasource.username=${USBSHIELD_TEST_DB_USERNAME:usbshield_test}",
    "spring.datasource.password=${USBSHIELD_TEST_DB_PASSWORD:}",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "usbshield.enrollment-token=integration-test-bootstrap"
})
public @interface IsolatedPostgresDb {}
