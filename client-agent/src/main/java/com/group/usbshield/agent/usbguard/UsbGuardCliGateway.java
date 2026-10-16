package com.group.usbshield.agent.usbguard;

import java.util.List;

/** Member 2 — Week 2: actual USBGuard CLI adapter, no mock or policy-file overwrite. */
public class UsbGuardCliGateway implements UsbGuardGateway {
    private final UsbGuardCommandRunner runner;
    private final UsbGuardOutputParser parser;
    private final UsbGuardPolicySynchronizer synchronizer;
    public UsbGuardCliGateway() {
        this(new UsbGuardCommandRunner(), new UsbGuardOutputParser(), null);
    }
    public UsbGuardCliGateway(UsbGuardCommandRunner runner, UsbGuardOutputParser parser, UsbGuardPolicySynchronizer synchronizer) {
        this.runner = runner; this.parser = parser;
        this.synchronizer = synchronizer == null ? new UsbGuardPolicySynchronizer(runner, parser, new UsbGuardRuleBuilder()) : synchronizer;
    }
    @Override public boolean isAvailable() { return health().reachable(); }
    @Override public List<UsbDeviceInfo> listDevices() { return parser.parseDevices(require(runner.run("list-devices"), "list-devices")); }
    @Override public List<UsbGuardRuleInfo> listRules() { return parser.parseRules(require(runner.run("list-rules"), "list-rules")); }
    @Override public boolean applyWhitelist(List<String> legacy) {
        try { synchronizer.reconcileLegacy(legacy); return true; }
        catch (RuntimeException e) { throw new IllegalStateException("USBGuard policy apply failed; do not ACK",e); }
    }
    public void reconcile(List<DeviceFingerprint> typed) { synchronizer.reconcile(typed); }
    @Override public UsbGuardHealth health() {
        try {
            String target=require(runner.run("get-parameter","ImplicitPolicyTarget"), "get-parameter");
            return target.toLowerCase(java.util.Locale.ROOT).contains("block") ? UsbGuardHealth.up()
                    : UsbGuardHealth.down("Unsafe ImplicitPolicyTarget: expected block");
        }
        catch (RuntimeException e) { return UsbGuardHealth.down(e.getMessage()); }
    }
    private static String require(CommandResult result, String action) {
        if (!result.success()) throw new IllegalStateException("USBGuard " + action + " exit=" + result.exitCode() + ": " + result.output());
        return result.output();
    }
}
