package com.group.usbshield.server.agentapi.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentUsbDeviceDto {

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F]{4}")
    private String vendorId;

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F]{4}")
    private String productId;

    @JsonAlias({"serialNumber", "serial_number"})
    @jakarta.validation.constraints.Size(max=150)
    private String serial;

    @JsonAlias({"deviceName", "device_name"})
    @jakarta.validation.constraints.Size(max=255)
    private String name;

    @JsonAlias({"deviceHash", "device_hash"})
    @jakarta.validation.constraints.Size(max=255)
    private String hash;

    @JsonAlias({"interface", "interfaces", "device_interfaces"})
    @jakarta.validation.constraints.Size(max=100)
    private String interfaceClass;
}
