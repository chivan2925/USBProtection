package com.group.usbshield.server.policy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhitelistedDeviceDto {

    private String vendorId;

    private String productId;

    private String serialNumber;

    private String deviceHash;

    private String deviceName;

    private String fingerprintValue;

    private String fingerprintType;
}
