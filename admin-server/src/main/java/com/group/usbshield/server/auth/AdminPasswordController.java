package com.group.usbshield.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/account")
@RequiredArgsConstructor
public class AdminPasswordController {
    private final AdminUserRepository accounts;
    private final PasswordEncoder encoder;

    public record ChangePassword(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    /** CSRF and ROLE_ADMIN are enforced by SecurityConfig. Other Admin sessions are revoked by the version filter. */
    @PostMapping("/password")
    @Transactional
    public Map<String, Boolean> change(@Valid @RequestBody ChangePassword body,
                                        Principal principal, HttpServletRequest request) {
        var user = accounts.lockByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!encoder.matches(body.currentPassword(), user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        if (!AdminPasswordPolicy.acceptable(body.newPassword()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 12 printable characters and at most 72 UTF-8 bytes");
        if (encoder.matches(body.newPassword(), user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must differ");
        user.setPasswordHash(encoder.encode(body.newPassword()));
        user.setCredentialVersion((user.getCredentialVersion() == null ? 0L : user.getCredentialVersion()) + 1);
        accounts.saveAndFlush(user);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("changed", true);
    }
}
