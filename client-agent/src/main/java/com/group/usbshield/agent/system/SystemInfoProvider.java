package com.group.usbshield.agent.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
public class SystemInfoProvider {

    public String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    /** Select an actual, non-loopback IPv4 address instead of /etc/hosts 127.0.1.1. */
    public String getIpAddress() {
        try {
            List<InetAddress> addresses = new ArrayList<>();
            var interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                var ips = iface.getInetAddresses();
                while (ips.hasMoreElements()) addresses.add(ips.nextElement());
            }
            return chooseNonLoopbackIpv4(addresses);
        } catch (Exception error) {
            log.warn("Could not determine non-loopback IPv4 address: {}", error.getMessage());
            return null;
        }
    }

    /** Choose the source address selected by the OS route to the actual Admin Server.
     * UDP connect does not send any datagram; it queries the kernel routing decision.
     * If unavailable, fall back to a non-loopback address without inventing a loopback IP. */
    public String getIpAddressForServer(String serverUrl) {
        if (serverUrl != null && !serverUrl.isBlank()) {
            try {
                URI uri = URI.create(serverUrl);
                if (uri.getHost() != null) {
                    int port = uri.getPort() > 0 ? uri.getPort() :
                            ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
                    try (DatagramSocket route = new DatagramSocket()) {
                        route.connect(new InetSocketAddress(uri.getHost(), port));
                        InetAddress local = route.getLocalAddress();
                        if (local instanceof Inet4Address && !local.isLoopbackAddress()
                                && !local.isAnyLocalAddress() && !local.isLinkLocalAddress()) {
                            return local.getHostAddress();
                        }
                    }
                }
            } catch (RuntimeException | java.net.SocketException e) {
                log.debug("Admin server route address unavailable: {}", e.getClass().getSimpleName());
            }
        }
        return getIpAddress();
    }

    static String chooseNonLoopbackIpv4(List<InetAddress> addresses) {
        if (addresses == null) return null;
        return addresses.stream()
                .filter(ip -> ip instanceof Inet4Address && !ip.isLoopbackAddress()
                        && !ip.isLinkLocalAddress() && !ip.isAnyLocalAddress())
                .sorted(Comparator.comparing((InetAddress ip) -> !ip.isSiteLocalAddress())
                        .thenComparing(InetAddress::getHostAddress))
                .map(InetAddress::getHostAddress)
                .findFirst().orElse(null);
    }

    /** Ubuntu metadata from os-release; no process execution or untrusted shell parsing. */
    public String getOperatingSystem() {
        try {
            List<String> lines = Files.readAllLines(Path.of("/etc/os-release"));
            String name = "Linux";
            String version = "";
            for (String line : lines) {
                if (line.startsWith("NAME=")) name = osReleaseValue(line.substring(5));
                else if (line.startsWith("VERSION_ID=")) version = osReleaseValue(line.substring(11));
            }
            return (name + " " + version).strip();
        } catch (Exception ignored) {
            return System.getProperty("os.name", "Unknown");
        }
    }

    static String osReleaseValue(String raw) {
        String value = raw.trim();
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    public String getMachineId() {
        Path machineIdPath = Path.of("/etc/machine-id");
        try {
            if (Files.exists(machineIdPath)) {
                return Files.readString(machineIdPath).trim();
            }
        } catch (Exception e) {
            log.warn("Could not read /etc/machine-id: {}", e.getMessage());
        }
        return UUID.randomUUID().toString();
    }
}
