package com.group.usbshield.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPasswordControllerTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    @Test void changeRequiresCurrentPasswordAndRotatesCredentialVersion() {
        var repo = mock(AdminUserRepository.class);
        var user = AdminUser.builder().username("admin")
                .passwordHash(encoder.encode("old-password-1234")).credentialVersion(7L).build();
        when(repo.lockByUsername("admin")).thenReturn(java.util.Optional.of(user));
        var controller = new AdminPasswordController(repo, encoder);
        Principal principal = () -> "admin";
        var request = new MockHttpServletRequest();
        var session = request.getSession();
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.change(new AdminPasswordController.ChangePassword("wrong", "new-password-1234"),
                        principal, request)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.change(new AdminPasswordController.ChangePassword("old-password-1234", "short"),
                        principal, request)).getStatusCode());
        assertEquals(7L, user.getCredentialVersion());
        assertTrue(controller.change(new AdminPasswordController.ChangePassword(
                "old-password-1234", "new-password-1234"), principal, request).get("changed"));
        assertEquals(8L, user.getCredentialVersion());
        assertTrue(encoder.matches("new-password-1234", user.getPasswordHash()));
        assertThrows(IllegalStateException.class, session::getCreationTime);
        verify(repo).saveAndFlush(user);
    }
}
