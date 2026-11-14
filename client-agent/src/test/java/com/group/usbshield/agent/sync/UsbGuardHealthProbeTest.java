package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.usbguard.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UsbGuardHealthProbeTest {
    @Test void neverClaimsHealthyWithoutRecentVerifiedPolicy() {
        var gateway = mock(UsbGuardGateway.class);
        when(gateway.health()).thenReturn(UsbGuardHealth.up());
        when(gateway.listRules()).thenReturn(List.of(new UsbGuardRuleInfo(1,"allow",null,null,null,null,
                "1: allow with-interface none-of { 08:*:* }")));
        var probe = new UsbGuardHealthProbe(gateway, true);
        probe.recordAppliedHashes(List.of());
        assertEquals("DEGRADED",probe.measure(false));
        assertEquals("HEALTHY",probe.measure(true));
    }
    @Test void foreignAllowsAndMissingDaemonAreNotHealthy() {
        var gateway = mock(UsbGuardGateway.class);
        when(gateway.health()).thenReturn(UsbGuardHealth.down("daemon unreachable"));
        var probe = new UsbGuardHealthProbe(gateway, true);
        assertEquals("UNAVAILABLE",probe.measure(true));
        when(gateway.health()).thenReturn(UsbGuardHealth.up());
        when(gateway.listRules()).thenReturn(List.of(new UsbGuardRuleInfo(1,"allow",null,null,null,null,
                "1: allow with-interface none-of { 08:*:* }"),
                new UsbGuardRuleInfo(2,"allow",null,null,null,null,"2: allow id 0951:1666")));
        assertEquals("DEGRADED",probe.measure(true));
    }
    @Test void missingOrModifiedManagedRuleIsDegraded() {
        var gateway = mock(UsbGuardGateway.class);
        when(gateway.health()).thenReturn(UsbGuardHealth.up());
        when(gateway.listRules()).thenReturn(List.of(new UsbGuardRuleInfo(1,"allow",null,null,null,null,
                "1: allow with-interface none-of { 08:*:* }")));
        var probe = new UsbGuardHealthProbe(gateway, true);
        probe.recordAppliedHashes(List.of("StableHash1234"));
        assertEquals("DEGRADED", probe.measure(true));
        when(gateway.listRules()).thenReturn(List.of(
                new UsbGuardRuleInfo(1,"allow",null,null,null,null,
                        "1: allow with-interface none-of { 08:*:* }"),
                new UsbGuardRuleInfo(2,"allow",null,null,null,"StableHash1234",
                        "2: allow hash \"StableHash1234\" label \"usbshield-managed\"")));
        assertEquals("HEALTHY", probe.measure(true));
        when(gateway.listRules()).thenReturn(List.of(
                new UsbGuardRuleInfo(1,"allow",null,null,null,null,
                        "1: allow with-interface none-of { 08:*:* }"),
                new UsbGuardRuleInfo(2,"allow",null,null,null,"DifferentHash999",
                        "2: allow hash \"DifferentHash999\" label \"usbshield-managed\"")));
        assertEquals("DEGRADED", probe.measure(true));
    }
    @Test void windowsMockNeverClaimsHealthy() {
        var probe = new UsbGuardHealthProbe(mock(UsbGuardGateway.class), false);
        assertEquals("UNKNOWN",probe.measure(true));
    }
}
