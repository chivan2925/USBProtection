package com.group.usbshield.server.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** Reject any authenticated Admin session established before a credential rotation. */
@RequiredArgsConstructor
public class AdminSessionVersionFilter extends OncePerRequestFilter {
    public static final String SESSION_VERSION = "usbshield.admin.credentialVersion";
    private final AdminUserRepository users;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.startsWith("/api/admin/") || path.equals("/api/auth/me")
                || path.equals("/api/auth/logout"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String && auth.getAuthorities().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()))) {
            HttpSession session = request.getSession(false);
            Object version = session == null ? null : session.getAttribute(SESSION_VERSION);
            var user = users.findByUsername(auth.getName());
            if (!(version instanceof Long) || user.isEmpty() ||
                    !Objects.equals(version, user.get().getCredentialVersion() == null ? 0L : user.get().getCredentialVersion())) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":401,\"message\":\"Admin credentials changed; please sign in again\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
