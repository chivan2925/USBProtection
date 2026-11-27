package com.group.usbshield.desktop;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** Windows Central has a dedicated HTTPS listener on the IPv4 loopback address. */
final class CentralTls {
    private CentralTls() {}

    static URI origin(URI value) {
        int port = value.getPort();
        if (!"https".equals(value.getScheme()) || !"127.0.0.1".equals(value.getHost())
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !value.getPath().matches("/?") || port == 0 || port > 65535) {
            throw new IllegalArgumentException("Central phải là HTTPS trên 127.0.0.1.");
        }
        return URI.create("https://127.0.0.1" + (port < 0 ? "" : ":" + port));
    }

    static SSLContext trust(Path caFile) throws IOException, GeneralSecurityException {
        var certificates = CertificateFactory.getInstance("X.509");
        var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        try (var input = Files.newInputStream(caFile)) {
            int index = 0;
            for (var certificate : certificates.generateCertificates(input)) {
                if (!(certificate instanceof X509Certificate ca) || ca.getBasicConstraints() < 0) {
                    throw new GeneralSecurityException("Desktop trust requires CA certificates");
                }
                ca.checkValidity();
                trustStore.setCertificateEntry("central-ca-" + index++, ca);
            }
            if (index == 0) throw new GeneralSecurityException("No Central CA certificate");
        }
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trustStore);
        var context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return context;
    }
}
