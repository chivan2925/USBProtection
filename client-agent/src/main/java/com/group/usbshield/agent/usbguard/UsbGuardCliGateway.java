package com.group.usbshield.agent.usbguard;

import java.util.List;
import java.util.Locale;

public class UsbGuardCliGateway implements UsbGuardGateway {
    private final UsbGuardCommandRunner runner;
    private final UsbGuardOutputParser parser;
    private final UsbGuardPolicySynchronizer synchronizer;

    public UsbGuardCliGateway() {
        this(new UsbGuardCommandRunner(), new UsbGuardOutputParser(), null);
    }

    public UsbGuardCliGateway(UsbGuardCommandRunner runner, UsbGuardOutputParser parser,
            UsbGuardPolicySynchronizer synchronizer) {
        this.runner = runner;
        this.parser = parser;
        this.synchronizer = synchronizer == null
                ? new UsbGuardPolicySynchronizer(runner, parser, new UsbGuardRuleBuilder()) : synchronizer;
    }

    @Override
    public boolean isAvailable() {
        return health().reachable();
    }

    @Override
    public List<UsbDeviceInfo> listDevices() {
        return parser.parseDevices(require(runner.run("list-devices"), "list-devices"));
    }

    @Override
    public List<UsbGuardRuleInfo> listRules() {
        return parser.parseRules(require(runner.run("list-rules"), "list-rules"));
    }

    @Override
    public boolean applyWhitelist(List<String> legacy) {
        try {
            synchronizer.reconcileLegacy(legacy);
            return true;
        } catch (RuntimeException failure) {
            throw new IllegalStateException("USBGuard policy apply failed; do not ACK", failure);
        }
    }

    public void reconcile(List<DeviceFingerprint> typed) {
        synchronizer.reconcile(typed);
    }

    @Override
    public UsbGuardHealth health() {
        try {
            String target = require(runner.run("get-parameter", "ImplicitPolicyTarget"), "get-parameter");
            return "block".equals(target.strip().toLowerCase(Locale.ROOT))
                    ? UsbGuardHealth.up()
                    : UsbGuardHealth.down("Unsafe ImplicitPolicyTarget: expected exactly block");
        } catch (RuntimeException failure) {
            return UsbGuardHealth.down(failure.getMessage());
        }
    }

    private static String require(CommandResult result, String action) {
        if (result == null || !result.success()) {
            String details = result == null ? "missing result"
                    : "exit=" + result.exitCode() + ": " + result.output();
            throw new IllegalStateException("USBGuard " + action + " " + details);
        }
        return result.output() == null ? "" : result.output();
    }
}
