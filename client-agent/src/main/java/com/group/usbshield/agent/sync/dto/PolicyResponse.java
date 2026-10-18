package com.group.usbshield.agent.sync.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyResponse {
    private Long policyVersion;
    @JsonAlias({"allowedDevices", "whitelistedDevices"})
    @Builder.Default
    private List<WhitelistedDeviceDto> whitelistedDevices = new ArrayList<>();
}
