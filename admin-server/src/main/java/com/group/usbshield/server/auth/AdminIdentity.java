package com.group.usbshield.server.auth;

import java.io.Serializable;
import java.security.Principal;

public record AdminIdentity(long adminUserId, long workspaceId, String username)
        implements Principal, Serializable {
    @Override public String getName() { return username; }
}
