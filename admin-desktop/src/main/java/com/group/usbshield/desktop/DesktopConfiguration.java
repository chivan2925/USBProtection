package com.group.usbshield.desktop;

import java.net.URI;
import java.nio.file.Path;

record DesktopConfiguration(URI origin, Path ca) {
    static DesktopConfiguration environment() {
        String ca = System.getenv("USBSHIELD_DESKTOP_CA");
        if (ca == null || ca.isBlank() || !Path.of(ca).isAbsolute()) {
            throw new IllegalArgumentException("USBSHIELD_DESKTOP_CA must be an absolute path");
        }
        var origin = URI.create(System.getenv().getOrDefault("USBSHIELD_CENTRAL_URL", "https://127.0.0.1"));
        return new DesktopConfiguration(CentralTls.origin(origin), Path.of(ca));
    }

    CentralClient connect() throws Exception {
        return new CentralClient(origin, CentralTls.trust(ca));
    }
}
