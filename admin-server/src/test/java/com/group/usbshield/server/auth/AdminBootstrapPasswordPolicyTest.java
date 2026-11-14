package com.group.usbshield.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapPasswordPolicyTest {
    @Test void unsafeFirstPasswordCannotSeedAnAdmin() {
        var users = mock(AdminUserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var bootstrap = new AdminBootstrap(users, encoder);
        ReflectionTestUtils.setField(bootstrap, "username", "admin");
        ReflectionTestUtils.setField(bootstrap, "password", "weak");
        assertThrows(IllegalStateException.class, () -> bootstrap.run(mock(ApplicationArguments.class)));
        verify(users, never()).save(any(AdminUser.class));
    }
    @Test void validInitialPasswordCreatesAdmin() {
        var users = mock(AdminUserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var bootstrap = new AdminBootstrap(users, encoder);
        ReflectionTestUtils.setField(bootstrap, "username", "admin");
        ReflectionTestUtils.setField(bootstrap, "password", "correct horse battery staple");
        when(encoder.encode(anyString())).thenReturn("hashed");
        bootstrap.run(mock(ApplicationArguments.class));
        verify(users).save(any(AdminUser.class));
    }
}
