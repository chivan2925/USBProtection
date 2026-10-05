package com.group.usbshield.agent.event;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.outbox.OutboxSpoolService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.system.SystemInfoProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class EventSenderService {

    private final EndpointManager endpointManager;
    private final ActiveUserResolver activeUserResolver;
    private final SystemInfoProvider systemInfoProvider;
    private final AdminServerClient adminServerClient;
    private final OutboxSpoolService outboxSpoolService;

    public EventSenderService(EndpointManager endpointManager,
                              ActiveUserResolver activeUserResolver,
                              SystemInfoProvider systemInfoProvider,
                              AdminServerClient adminServerClient,
                              OutboxSpoolService outboxSpoolService) {
        this.endpointManager = endpointManager;
        this.activeUserResolver = activeUserResolver;
        this.systemInfoProvider = systemInfoProvider;
        this.adminServerClient = adminServerClient;
        this.outboxSpoolService = outboxSpoolService;
    }

    /**
     * Thu thập thông tin phiên đăng nhập Ubuntu và gửi sự kiện cắm/rút USB lên Admin Server
     */
    public void emitUsbEvent(Map<String, Object> device, String eventType, String decision) {
        // Lấy thông tin Ubuntu user đang hoạt động tại máy (qua loginctl resolver)
        ActiveUserInfo activeUser = activeUserResolver.resolveCurrentActiveUser();
        String username = activeUser != null ? activeUser.getUsername() : "UNKNOWN";
        Long uid = activeUser != null ? activeUser.getUid() : null;
        String sessionId = activeUser != null ? activeUser.getSessionId() : null;
        String sessionType = activeUser != null ? activeUser.getSessionType() : null;
        String seat = activeUser != null ? activeUser.getSeat() : null;

        EndpointRegistration registration = endpointManager.getCurrentRegistration();
        String endpointId = registration != null ? registration.getEndpointId() : "UNENROLLED";
        String agentToken = registration != null ? registration.getAgentToken() : "";

        UsbEventPayload payload = UsbEventPayload.builder()
                .endpointId(endpointId)
                .hostname(systemInfoProvider.getHostname())
                .linuxUsername(username)
                .linuxUid(uid)
                .sessionId(sessionId)
                .sessionType(sessionType)
                .seat(seat)
                .device(device)
                .eventType(eventType)
                .decision(decision)
                .occurredAt(Instant.now())
                .build();

        sendPayload(payload);
    }

    public void sendPayload(UsbEventPayload payload) {
        EndpointRegistration registration = endpointManager.getCurrentRegistration();
        String endpointId = registration != null ? registration.getEndpointId() : payload.getEndpointId();
        String agentToken = registration != null ? registration.getAgentToken() : "";

        log.info("[USB-EVENT] Detected USB {} by user '{}' ({}) -> Decision: {}",
                payload.getEventType(), payload.getLinuxUsername(), payload.getDevice(), payload.getDecision());

        // Thử gửi trực tiếp lên Admin Server
        boolean sent = false;
        if (registration != null && registration.getEndpointId() != null) {
            sent = adminServerClient.sendEvent(endpointId, agentToken, payload);
        }

        // Nếu gửi thất bại hoặc chưa có kết nối, lưu vào hàng đợi đệm ngoại tuyến
        if (!sent) {
            outboxSpoolService.spool(payload);
        }
    }
}
