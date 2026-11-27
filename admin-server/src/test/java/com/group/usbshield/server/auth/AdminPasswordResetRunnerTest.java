package com.group.usbshield.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminPasswordResetRunnerTest {
    @Test void refusesToInferTargetFromBootstrapUsername() {
        var env = new MockEnvironment()
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("USBSHIELD_ADMIN_RESET_PASSWORD", "synthetic-reset-secret")
                .withProperty("usbshield.admin.username", "previous-bootstrap-name");
        var transactions = mock(TransactionTemplate.class);
        var accounts = mock(AdminUserRepository.class);
        var runner = new AdminPasswordResetRunner(accounts, mock(PasswordEncoder.class), transactions,
                mock(WorkspaceRepository.class), mock(WorkspaceAudit.class), env);

        var missing = assertThrows(IllegalStateException.class, () -> runner.run(mock(ApplicationArguments.class)));
        assertTrue(missing.getMessage().contains("USBSHIELD_ADMIN_RESET_USERNAME"));
        verifyNoInteractions(transactions, accounts);
    }
}
