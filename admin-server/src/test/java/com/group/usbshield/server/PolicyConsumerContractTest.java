package com.group.usbshield.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.server.policy.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PolicyConsumerContractTest {
    @TempDir Path output;

    @Test void serverPolicyDeserializesWithActualUnmodifiedM3SourceDtos() throws Exception {
        Path root=Path.of("../client-agent/src/main/java");
        if(!Files.exists(root))root=Path.of("client-agent/src/main/java");
        Path dto=root.resolve("com/group/usbshield/agent/sync/dto");
        var compiler=ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK required to compile actual consumer DTOs").isNotNull();
        int result=compiler.run(null,null,null,"-classpath",System.getProperty("java.class.path"),
            "-processor","lombok.launch.AnnotationProcessorHider$AnnotationProcessor",
            "-d",output.toString(),dto.resolve("PolicyResponse.java").toString(),dto.resolve("WhitelistedDeviceDto.java").toString());
        assertThat(result).isZero();
        var mapper=new ObjectMapper();
        var policy=AgentPolicyResponse.builder().policyVersion(2L).whitelistedDevices(List.of(
            WhitelistedDeviceDto.builder().deviceHash("known-hash").fingerprintValue("known-hash").fingerprintType("HASH")
                .vendorId("0951").productId("1666").deviceName("USB").serialNumber("serial").build())).build();
        String json=mapper.writeValueAsString(policy);
        assertThat(mapper.readTree(json).has("allowedDevices")).isFalse();
        try(var loader=new URLClassLoader(new java.net.URL[]{output.toUri().toURL()},getClass().getClassLoader())){
            Class<?> consumer=loader.loadClass("com.group.usbshield.agent.sync.dto.PolicyResponse");
            Object parsed=mapper.readValue(json,consumer);
            assertThat(consumer.getMethod("getPolicyVersion").invoke(parsed)).isEqualTo(2L);
            var entries=(List<?>)consumer.getMethod("getWhitelistedDevices").invoke(parsed);
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).getClass().getMethod("getDeviceHash").invoke(entries.get(0))).isEqualTo("known-hash");
        }
    }
}
