package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.usbguard.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsbGuardPolicyOrderHealthRegressionTest {
    private static UsbGuardRuleInfo rule(long id, String target, String hash, String raw) {
        return new UsbGuardRuleInfo(id, target, null, null, null, hash, raw);
    }
    @Test void denyBeforeManagedAllowCannotBeHealthy() {
        var gateway = mock(UsbGuardGateway.class);
        when(gateway.health()).thenReturn(UsbGuardHealth.up());
        var base = rule(1, "allow", null, "1: allow with-interface none-of { 08:*:* }");
        var deny = rule(2, "block", "Hash42", "2: block hash \"Hash42\"");
        var allow = rule(3, "allow", "Hash42", "3: allow hash \"Hash42\" label \"usbshield-managed\"");
        when(gateway.listRules()).thenReturn(List.of(base, deny, allow));
        var probe = new UsbGuardHealthProbe(gateway, true);
        probe.recordAppliedHashes(List.of("Hash42"));
        assertEquals("DEGRADED", probe.measure(true));
        when(gateway.listRules()).thenReturn(List.of(base, allow));
        assertEquals("HEALTHY", probe.measure(true));
    }
    @Test void denyBeforeNonStorageBaseIsDegraded() {
        var gateway = mock(UsbGuardGateway.class);
        when(gateway.health()).thenReturn(UsbGuardHealth.up());
        when(gateway.listRules()).thenReturn(List.of(
                rule(1, "block", null, "1: block"),
                rule(2, "allow", null, "2: allow with-interface none-of { 08:*:* }")));
        var probe = new UsbGuardHealthProbe(gateway, true);
        probe.recordAppliedHashes(List.of());
        assertEquals("DEGRADED", probe.measure(true));
    }
}
