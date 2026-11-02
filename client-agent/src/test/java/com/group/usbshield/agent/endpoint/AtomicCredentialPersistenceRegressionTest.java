package com.group.usbshield.agent.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AtomicCredentialPersistenceRegressionTest {
    @TempDir Path dir;

    @Test void repeatedAtomicReplacementLeavesValidOnlyCredentialFileAndNoTemps() throws Exception {
        AgentProperties settings = new AgentProperties();
        settings.setDataDir(dir.toString());
        EndpointManager manager = new EndpointManager(settings, mock(SystemInfoProvider.class),
                mock(AdminServerClient.class), new ObjectMapper());
        manager.init();
        assertTrue(manager.saveRegistration(EndpointRegistration.builder().endpointId("EP-A")
                .agentToken("private-credential-a").currentPolicyVersion(1L).build()));
        assertTrue(manager.saveRegistration(EndpointRegistration.builder().endpointId("EP-A")
                .agentToken("private-credential-b").currentPolicyVersion(2L).build()));
        var file = dir.resolve("endpoint.json");
        var parsed = new ObjectMapper().readValue(file.toFile(), EndpointRegistration.class);
        assertEquals("private-credential-b", parsed.getAgentToken());
        assertEquals(2L, parsed.getCurrentPolicyVersion());
        try (var entries = Files.list(dir)) {
            assertEquals(1L, entries.count(), "No partial .endpoint temp is left behind");
        }
        if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(file)), "Secrets must not be world readable");
        }
    }
}
