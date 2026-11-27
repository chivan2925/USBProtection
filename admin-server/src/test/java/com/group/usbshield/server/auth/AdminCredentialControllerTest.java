package com.group.usbshield.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class AdminCredentialControllerTest {
    @Test void previouslyInvalidatedSessionDoesNotMaskSuccessfulWrite() {
        var credentials = mock(AdminCredentialService.class);
        var request = spy(new MockHttpServletRequest());
        var session = mock(HttpSession.class);
        doReturn(session).when(request).getSession(false);
        doThrow(new IllegalStateException("already invalidated")).when(session).invalidate();
        var controller = new AdminCredentialController(credentials, new ObjectMapper());
        var identity = new AdminIdentity(1L, 2L, "original");
        request.setContent("""
                {"currentPassword":"synthetic-secret","newUsername":"renamed"}
                """.getBytes(StandardCharsets.UTF_8));

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of()));
        try {
            assertDoesNotThrow(() -> controller.change(identity, 1L, request));
            verify(credentials).change(identity, 1L, "synthetic-secret", "renamed", null);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
