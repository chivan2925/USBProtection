package com.group.usbshield.server.policy;

import java.util.regex.Pattern;

/** Compatible with Agent UsbGuardRuleBuilder hash fingerprint validation. */
public final class UsbHashIdentity {
    private static final Pattern SAFE = Pattern.compile("[a-zA-Z0-9+/=_:.\\-]{1,255}");
    private static final Pattern ALNUM = Pattern.compile(".*[a-zA-Z0-9].*");
    private static final Pattern VID_PID = Pattern.compile("[a-fA-F0-9]{4}:[a-fA-F0-9]{4}");
    private UsbHashIdentity() {}
    public static boolean valid(String hash) {
        return hash != null && SAFE.matcher(hash).matches() && ALNUM.matcher(hash).matches()
                && !VID_PID.matcher(hash).matches();
    }
}
