package com.group.usbshield.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LocalHttpsFixture implements AutoCloseable {
    private static final String FIXTURE_PASSWORD = "synthetic-fixture";
    final HttpsServer server;
    final Path ca;

    LocalHttpsFixture(Path directory, String san) throws Exception {
        Files.createDirectories(directory);
        var store = directory.resolve("fixture.p12");
        ca = directory.resolve("fixture.pem");
        keytool("-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=USBShield desktop test", "-ext", "BC=ca:true", "-ext", "SAN=" + san,
                "-validity", "3", "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", FIXTURE_PASSWORD, "-noprompt");
        keytool("-exportcert", "-rfc", "-alias", "fixture", "-keystore", store.toString(),
                "-storepass", FIXTURE_PASSWORD, "-file", ca.toString());
        var keys = KeyStore.getInstance("PKCS12");
        try (var contents = Files.newInputStream(store)) {
            keys.load(contents, FIXTURE_PASSWORD.toCharArray());
        }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, FIXTURE_PASSWORD.toCharArray());
        var tls = SSLContext.getInstance("TLS");
        tls.init(managers.getKeyManagers(), null, null);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 10);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
    }

    URI origin() { return URI.create("https://127.0.0.1:" + server.getAddress().getPort()); }

    static void respond(HttpExchange exchange, int status, String json) throws IOException {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            if (status == 204) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static void keytool(String... args) throws Exception {
        var executable = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        var command = new java.util.ArrayList<String>();
        command.add(executable);
        command.addAll(java.util.List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(25, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertTrue(false, "keytool fixture preparation timed out");
        }
        assertEquals(0, process.exitValue(), () -> "keytool fixture failed");
    }

    @Override public void close() { server.stop(0); }
}
