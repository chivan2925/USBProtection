package com.group.usbshield.agent.usbguard;

/** Member 2 — Week 2: USBGuard command exit code and collected output (guide section 12). */
public record CommandResult(int exitCode, String output) {
    public boolean success() {
        return exitCode == 0;
    }
}
