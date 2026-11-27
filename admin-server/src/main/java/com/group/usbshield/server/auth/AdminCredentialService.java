package com.group.usbshield.server.auth;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCredentialService {
    enum Rejection { STALE_SESSION, WRONG_PASSWORD, INVALID_CHANGE, USERNAME_TAKEN }

    static final class Rejected extends RuntimeException {
        private final Rejection reason;

        Rejected(Rejection reason) {
            super(reason.name());
            this.reason = reason;
        }

        Rejection reason() { return reason; }
    }

    private final AdminUserRepository users;
    private final WorkspaceRepository workspaces;
    private final PasswordEncoder encoder;
    private final WorkspaceAudit audit;

    @Transactional
    public void change(AdminIdentity identity, Long sessionVersion, String currentPassword,
                       String newUsername, String newPassword) {
        var user = users.lockById(identity.adminUserId()).orElseThrow(() -> new Rejected(Rejection.STALE_SESSION));
        // Recheck under the lock in case another change passed the session filter first.
        if (sessionVersion == null || !Objects.equals(sessionVersion, user.getCredentialVersion())
                || !workspaces.existsByIdAndOwnerAdminId(identity.workspaceId(), identity.adminUserId())) {
            throw new Rejected(Rejection.STALE_SESSION);
        }
        if (!AdminPasswordPolicy.withinEncoderLimit(currentPassword)
                || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new Rejected(Rejection.WRONG_PASSWORD);
        }

        boolean rename = newUsername != null && !newUsername.equals(user.getUsername());
        if (newUsername != null && (newUsername.isBlank() || !newUsername.equals(newUsername.strip())
                || newUsername.length() > 100 || newUsername.codePoints().anyMatch(Character::isISOControl))) {
            throw new Rejected(Rejection.INVALID_CHANGE);
        }
        if (newPassword != null && (!AdminPasswordPolicy.acceptable(newPassword)
                || encoder.matches(newPassword, user.getPasswordHash()))) {
            throw new Rejected(Rejection.INVALID_CHANGE);
        }
        if (!rename && newPassword == null) {
            throw new Rejected(Rejection.INVALID_CHANGE);
        }
        if (rename && users.existsByUsername(newUsername)) {
            throw new Rejected(Rejection.USERNAME_TAKEN);
        }

        if (rename) user.setUsername(newUsername);
        if (newPassword != null) user.setPasswordHash(encoder.encode(newPassword));
        user.advanceCredentialVersion();
        users.flush();

        String target = Long.toString(user.getId());
        if (rename) audit.record(identity, "ADMIN_USERNAME_CHANGED", target);
        if (newPassword != null) audit.record(identity, "ADMIN_PASSWORD_CHANGED", target);
    }
}
