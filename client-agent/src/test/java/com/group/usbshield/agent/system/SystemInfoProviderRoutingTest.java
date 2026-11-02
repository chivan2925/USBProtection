package com.group.usbshield.agent.system;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SystemInfoProviderRoutingTest {
    @Test void neverSelectsHostsFileLoopbackInsteadOfVmNetworkAddress() throws Exception {
        assertEquals("192.168.23.133", SystemInfoProvider.chooseNonLoopbackIpv4(List.of(
                InetAddress.getByName("127.0.1.1"), InetAddress.getByName("192.168.23.133"))));
        assertNull(SystemInfoProvider.chooseNonLoopbackIpv4(List.of(
                InetAddress.getByName("127.0.0.1"))));
    }

    @Test void routeLookupNeverReportsLoopbackWhenTargetIsLoopback() {
        String ip = new SystemInfoProvider().getIpAddressForServer("http://127.0.0.1:18080");
        assertTrue(ip == null || !ip.startsWith("127."),
                "Never misreport /etc/hosts loopback as Ubuntu Agent LAN IP");
    }
}
