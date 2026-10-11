package com.group.usbshield.server.policy.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentPolicyResponse {

    private Long policyVersion;

    @Builder.Default
    @JsonAlias({"whitelistedDevices"})
    private List<WhitelistedDeviceDto> allowedDevices = new ArrayList<>();

    public List<WhitelistedDeviceDto> getWhitelistedDevices() {
        return allowedDevices;
    }

    public void setWhitelistedDevices(List<WhitelistedDeviceDto> whitelistedDevices) {
        this.allowedDevices = whitelistedDevices;
    }
}
