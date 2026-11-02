package com.group.usbshield.agent.system;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SystemInfoProviderRegressionTest {
    @Test
    void ignoresLoopbackLinkLocalAndIpv6AndSelectsVmnetIpv4() throws Exception {
        assertEquals("192.168.23.133", SystemInfoProvider.chooseNonLoopbackIpv4(List.of(
                InetAddress.getByName("127.0.1.1"), InetAddress.getByName("169.254.2.3"),
                InetAddress.getByName("fe80::1"), InetAddress.getByName("192.168.23.133"))));
        assertNull(SystemInfoProvider.chooseNonLoopbackIpv4(List.of(InetAddress.getByName("127.0.1.1"))));
    }

    @Test
    void osReleaseQuotesAreHandledWithoutShellCommands() {
        assertEquals("Ubuntu", SystemInfoProvider.osReleaseValue("\"Ubuntu\""));
        assertEquals("24.04", SystemInfoProvider.osReleaseValue("\"24.04\""));
    }
}
