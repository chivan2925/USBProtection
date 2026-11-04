package com.group.usbshield.server.endpoint;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/endpoints")
@RequiredArgsConstructor
public class AdminEndpointController {
    private final EndpointRepository endpoints;
    @Value("${usbshield.offline-after-seconds:90}") private long offlineAfter;

    public record EndpointResponse(String endpointId, String hostname, String ipAddress,
            String operatingSystem, String currentUser, String status, Instant lastSeen,
            Long policyVersion, Long appliedPolicyVersion, String usbGuardStatus,
            Instant usbGuardCheckedAt, boolean usbGuardStale) {}
    public record EndpointPage(List<EndpointResponse> content, int page, int size,
            long totalElements, int totalPages, long onlineEndpoints, long offlineEndpoints) {}

    private EndpointResponse view(Endpoint e, Instant now) {
        boolean online = e.getLastSeen() != null && e.getLastSeen().isAfter(now.minusSeconds(offlineAfter));
        boolean stale = e.getUsbGuardCheckedAt() == null || !online ||
                e.getUsbGuardCheckedAt().isBefore(now.minusSeconds(offlineAfter));
        String observed = e.getUsbGuardReportedStatus();
        String effective = stale || observed == null ? "UNKNOWN" : observed;
        if ("HEALTHY".equals(effective) &&
                !java.util.Objects.equals(e.getPolicyVersion(), e.getAppliedPolicyVersion()))
            effective = "DEGRADED";
        return new EndpointResponse(e.getEndpointId(), e.getHostname(), e.getIpAddress(),
                e.getOperatingSystem(), e.getCurrentUser(), online ? "ONLINE" : "OFFLINE",
                e.getLastSeen(), e.getPolicyVersion(), e.getAppliedPolicyVersion(),
                effective, e.getUsbGuardCheckedAt(), stale);
    }

    /** New bounded query contract. Filtering precedes pagination in PostgreSQL. */
    @Transactional(readOnly = true)
    @GetMapping("/page")
    public EndpointPage page(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) String status) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid endpoint pagination");
        if (q != null && q.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endpoint search too long");
        if (status != null && !List.of("online", "offline").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid endpoint status");
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(offlineAfter);
        Specification<Endpoint> filter = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) {
                var lastSeen = root.<Instant>get("lastSeen");
                predicates.add("online".equals(status)
                        ? cb.greaterThan(lastSeen, cutoff)
                        : cb.or(cb.isNull(lastSeen), cb.lessThanOrEqualTo(lastSeen, cutoff)));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("endpointId")), pattern, '!'),
                        cb.like(cb.lower(root.<String>get("hostname")), pattern, '!'),
                        cb.like(cb.lower(root.<String>get("ipAddress")), pattern, '!'),
                        cb.like(cb.lower(root.<String>get("currentUser")), pattern, '!')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = endpoints.findAll(filter, PageRequest.of(page, size, Sort.by("endpointId")));
        long online = endpoints.countByLastSeenAfter(cutoff);
        long all = endpoints.count();
        return new EndpointPage(result.getContent().stream().map(e -> view(e, now)).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(), online, all - online);
    }

    /** Deprecated compatibility contract, capped to avoid an unbounded response; new UI uses /page. */
    @Deprecated
    @Transactional(readOnly = true)
    @GetMapping
    public List<EndpointResponse> list() {
        Instant now = Instant.now();
        return endpoints.findAll(PageRequest.of(0, 100, Sort.by("endpointId")))
                .getContent().stream().map(e -> view(e, now)).toList();
    }

    @GetMapping("/{endpointId}")
    @Transactional(readOnly = true)
    public EndpointResponse detail(@PathVariable String endpointId) {
        return view(endpoints.findByEndpointId(endpointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Endpoint not found")), Instant.now());
    }
}
