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

    private String vendorId;

    private String productId;

    @JsonAlias({"serialNumber", "serial_number"})
    private String serial;

    @JsonAlias({"deviceName", "device_name"})
    private String name;

    @JsonAlias({"deviceHash", "device_hash"})
    private String hash;

    @JsonAlias({"interface", "interfaces", "device_interfaces"})
    private String interfaceClass;
}
