package com.group.usbshield.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapPasswordPolicyTest {
    @Test void unsafeFirstPasswordCannotSeedAnAdmin() {
        var users = mock(AdminUserRepository.class);
        var provisioning = mock(AdminProvisioningService.class);
        var bootstrap = new AdminBootstrap(users, provisioning);
        ReflectionTestUtils.setField(bootstrap, "username", "admin");
        ReflectionTestUtils.setField(bootstrap, "password", "weak");
        assertThrows(IllegalStateException.class, () -> bootstrap.run(mock(ApplicationArguments.class)));
        verifyNoInteractions(provisioning);
    }
    @Test void validInitialPasswordCreatesAdmin() {
        var users = mock(AdminUserRepository.class);
        var provisioning = mock(AdminProvisioningService.class);
        var bootstrap = new AdminBootstrap(users, provisioning);
        ReflectionTestUtils.setField(bootstrap, "username", "admin");
        ReflectionTestUtils.setField(bootstrap, "password", "correct horse battery staple");
        bootstrap.run(mock(ApplicationArguments.class));
        verify(provisioning).create("admin", "correct horse battery staple");
    }
}
