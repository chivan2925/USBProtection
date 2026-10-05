package com.group.usbshield.agent.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistedDeviceDto {
    private String vendorId;
    private String productId;
    private String serialNumber;
    private String deviceHash;
    private String deviceName;
    private String fingerprintValue;
}
