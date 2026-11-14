package com.group.usbshield.server;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Deletes only the schema generated for this test class after its cases finish. */
public final class TestPostgresSchemaCleanup implements AfterAllCallback {
    @Override public void afterAll(ExtensionContext context) throws Exception {
        TestPostgresSchemas.cleanup(context.getRequiredTestClass());
    }
}
