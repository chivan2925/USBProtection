package com.group.usbshield.agent.usbguard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component("realUsbGuardGateway")
@Primary
public class AdaptiveUsbGuardGateway implements UsbGuardGateway {

    private static final String DEFAULT_RULES_PATH = "/etc/usbguard/rules.conf";
    private static final String BASE_RULE = "allow with-interface none-of { 08:*:* }";

    @Override
    public boolean isAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) {
            return true; // Môi trường dev Windows luôn coi là sẵn sàng để test
        }

        try {
            Process process = new ProcessBuilder("usbguard", "list-rules").start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("[UsbGuardGateway] USBGuard daemon is not reachable via CLI: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void applyWhitelist(List<String> whitelistRules) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) {
            log.info("[UsbGuardGateway] Non-Linux environment ('{}') -> Simulated applying {} whitelist rules:", os, whitelistRules.size());
            whitelistRules.forEach(r -> log.info("[UsbGuardGateway]   -> allow: {}", r));
            return;
        }

        log.info("[UsbGuardGateway] Applying {} whitelist rules to /etc/usbguard/rules.conf...", whitelistRules.size());
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# ========================================================");
            lines.add("# USBShield Generated Policy - DO NOT EDIT MANUALLY");
            lines.add("# ========================================================");
            lines.add("# 1. Base rule: Allow all non-mass-storage peripherals (mouse, keyboard, etc.)");
            lines.add(BASE_RULE);
            lines.add("");
            lines.add("# 2. Whitelisted USB Mass Storage devices (Admin approved)");

            for (String rule : whitelistRules) {
                String trimmed = rule.trim();
                if (!trimmed.startsWith("allow")) {
                    trimmed = "allow " + trimmed;
                }
                lines.add(trimmed);
            }
            lines.add("");

            Path targetPath = Path.of(DEFAULT_RULES_PATH);
            if (Files.isWritable(targetPath.getParent()) || Files.isWritable(targetPath)) {
                Path tempFile = Files.createTempFile("usbguard-rules", ".tmp");
                Files.write(tempFile, lines, StandardCharsets.UTF_8);
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("[UsbGuardGateway] Successfully updated {}", DEFAULT_RULES_PATH);

                // Reload USBGuard daemon
                reloadDaemon();
            } else {
                log.warn("[UsbGuardGateway] Cannot write to {} (insufficient permissions, requires root/sudo)", DEFAULT_RULES_PATH);
            }
        } catch (Exception e) {
            log.error("[UsbGuardGateway] Failed to apply whitelist to USBGuard: {}", e.getMessage(), e);
        }
    }

    private void reloadDaemon() {
        try {
            Process process = new ProcessBuilder("systemctl", "reload", "usbguard").start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.info("[UsbGuardGateway] Successfully reloaded usbguard.service");
            } else {
                log.warn("[UsbGuardGateway] Could not reload usbguard.service via systemctl (exit code: {})", process.exitValue());
            }
        } catch (Exception e) {
            log.debug("[UsbGuardGateway] Failed to execute systemctl reload usbguard: {}", e.getMessage());
        }
    }
}
