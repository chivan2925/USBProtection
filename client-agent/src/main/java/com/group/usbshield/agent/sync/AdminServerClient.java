package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.net.URI;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

@Slf4j
@Component
public class AdminServerClient {

    private final RestClient restClient;
    private final AgentProperties properties;
    // Same-thread status from the most recent sendEvent() call. Outbox invokes
    // sendEvent synchronously, so mock/test compatibility is preserved.
    private final ThreadLocal<Boolean> permanentEventRejection = ThreadLocal.withInitial(() -> false);

    /** Only HTTP 400/409/413/422 are permanently invalid for the same payload.
     * Auth failures, rate limits, server failures and transport errors remain retryable. */
    public boolean lastEventWasPermanentlyRejected() {
        return permanentEventRejection.get();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminServerClient(AgentProperties properties, org.springframework.core.env.Environment environment) {
        // The active 'live' profile always enforces HTTPS even if external
        // property sources attempt to override require-https=false.
        this(properties, productionRestClient(properties,
                environment.acceptsProfiles(org.springframework.core.env.Profiles.of("live"))));
    }

    /** Standalone construction for non-Spring development/test harnesses. */
    public AdminServerClient(AgentProperties properties) {
        this(properties, productionRestClient(properties, false));
    }

    /** Fail before any HTTP request if the live Agent was misconfigured. */
    static void validateTransport(AgentProperties properties, boolean live) {
        String value = properties.getServerUrl();
        try {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("missing URL");
            URI uri = new URI(value).parseServerAuthority();
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null ||
                    uri.getRawQuery() != null ||
                    (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath())) ||
                    !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                throw new IllegalArgumentException("expected bare HTTP(S) server origin");
            if ((properties.isRequireHttps() || live) && !"https".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException("live Agent requires HTTPS; HTTP fallback is forbidden");
            if (properties.getTlsCaCertificate() != null && !properties.getTlsCaCertificate().isBlank() &&
                    !"https".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException("custom CA requires HTTPS");
        } catch (Exception invalid) {
            throw new IllegalStateException("Invalid USBShield Agent transport configuration: " + invalid.getMessage(), invalid);
        }
    }

    /** Custom CA is trusted through PKIX; HttpsURLConnection's default hostname verifier stays active. */
    private static javax.net.ssl.SSLSocketFactory pinnedCaSocketFactory(AgentProperties properties) {
        String caFile = properties.getTlsCaCertificate();
        if (caFile == null || caFile.isBlank()) return null; // JVM default trust roots + hostname validation
        try {
            Path path = Path.of(caFile);
            if (!path.isAbsolute() || !Files.isRegularFile(path) || Files.isSymbolicLink(path))
                throw new IllegalArgumentException("CA path must be an absolute regular PEM file");
            var certificateFactory = CertificateFactory.getInstance("X.509");
            java.util.Collection<? extends java.security.cert.Certificate> certificates;
            try (var input = Files.newInputStream(path)) {
                certificates = certificateFactory.generateCertificates(input);
            }
            if (certificates.isEmpty()) throw new IllegalArgumentException("No X.509 CA certificates in PEM");
            var store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            int index = 0;
            for (var cert : certificates) store.setCertificateEntry("usbshield-ca-" + index++, cert);
            var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            var context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), new SecureRandom());
            return context.getSocketFactory();
        } catch (Exception invalid) {
            throw new IllegalStateException("Unable to configure HTTPS CA certificate: " + invalid.getMessage(), invalid);
        }
    }

    private static RestClient productionRestClient(AgentProperties properties, boolean live) {
        validateTransport(properties, live);
        var sslSocketFactory = pinnedCaSocketFactory(properties);
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                // Never forward Agent credentials across redirects or a scheme downgrade.
                connection.setInstanceFollowRedirects(false);
                if (sslSocketFactory != null) {
                    if (!(connection instanceof HttpsURLConnection tls))
                        throw new IOException("CA configured for a non-HTTPS request");
                    tls.setSSLSocketFactory(sslSocketFactory);
                    // Intentionally never call setHostnameVerifier or disable certificate checks.
                }
            }
        };
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.getServerUrl())
                .build();
    }

    public AdminServerClient(AgentProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public String getServerUrl() { return properties.getServerUrl(); }

    /**
     * Đăng ký Endpoint mới với Admin Server (POST /api/agent/enroll)
     */
    public Optional<EnrollmentResponse> enroll(EnrollmentRequest request) {
        try {
            log.info("Sending enrollment request to {} for host: {}", properties.getServerUrl(), request.getHostname());
            EnrollmentResponse response = restClient.post()
                    .uri("/api/agent/enroll")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EnrollmentResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Enrollment failed (Server may be offline): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Gửi Heartbeat định kỳ (POST /api/agent/heartbeat)
     */
    public Optional<HeartbeatResponse> sendHeartbeat(HeartbeatRequest request, String agentToken) {
        try {
            HeartbeatResponse response = restClient.post()
                    .uri("/api/agent/heartbeat")
                    .header("X-Endpoint-Id", request.getEndpointId())
                    .header("X-Agent-Token", agentToken != null ? agentToken : "")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(HeartbeatResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.debug("Heartbeat failed (Server offline): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Kéo danh sách Whitelist cho chính endpoint này (GET /api/agent/policy)
     */
    public Optional<PolicyResponse> fetchPolicy(String endpointId, String agentToken) {
        try {
            log.info("Fetching latest policy for endpoint {}", endpointId);
            PolicyResponse response = restClient.get()
                    .uri("/api/agent/policy")
                    .header("X-Endpoint-Id", endpointId)
                    .header("X-Agent-Token", agentToken != null ? agentToken : "")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PolicyResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Failed to fetch policy: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Xác nhận đã áp dụng phiên bản policy mới thành công (POST /api/agent/policy/ack)
     */
    public boolean acknowledgePolicy(String endpointId, String agentToken, PolicyAckRequest request) {
        try {
            restClient.post()
                    .uri("/api/agent/policy/ack")
                    .header("X-Endpoint-Id", endpointId)
                    .header("X-Agent-Token", agentToken != null ? agentToken : "")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Acknowledged policy version {} to server", request.getAppliedPolicyVersion());
            return true;
        } catch (Exception e) {
            log.warn("Failed to acknowledge policy: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gửi sự kiện cắm/rút USB (POST /api/agent/events)
     */
    public boolean sendEvent(String endpointId, String agentToken, UsbEventPayload payload) {
        permanentEventRejection.set(false);
        try {
            restClient.post()
                    .uri("/api/agent/events")
                    .header("X-Endpoint-Id", endpointId)
                    .header("X-Agent-Token", agentToken != null ? agentToken : "")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully sent USB event type={} to Admin Server", payload.getEventType());
            return true;
        } catch (org.springframework.web.client.RestClientResponseException rejected) {
            int code = rejected.getStatusCode().value();
            boolean futureSkew = code == 400 && rejected.getResponseBodyAsString()
                    .contains("future clock skew");
            permanentEventRejection.set((code == 400 && !futureSkew) || code == 409 || code == 413 || code == 422);
            log.warn("Admin Server rejected USB event (HTTP {}); audit bytes stay on disk if undelivered", code);
            return false;
        } catch (Exception e) {
            log.warn("Failed to send USB event to server: {}", e.getMessage());
            return false;
        }
    }
}
