package com.group.usbshield.agent.session;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActiveUserInfo {
    private String username;
    private Long uid;
    private String sessionId;
    private String sessionType;
    private String seat;
}
