package com.group.usbshield.server.event;

import com.group.usbshield.server.device.UsbDeviceRepository;
import com.group.usbshield.server.endpoint.EndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminEventPagingLimitTest {
    private AdminEventController controller() {
        return new AdminEventController(mock(UsbEventRepository.class),
                mock(UsbDeviceRepository.class), mock(EndpointRepository.class));
    }
    @Test void rejectsUnboundedPagesAndFiltersBeforeDatabase() {
        var c = controller();
        assertThrows(ResponseStatusException.class, () -> c.list(100001,20,
                null,null,null,null,null,null,null,null,null,null));
        assertThrows(ResponseStatusException.class, () -> c.list(0,20,
                null,null,null,null,null,null,null,"a".repeat(201),null,null));
        assertThrows(ResponseStatusException.class, () -> c.list(0,20,
                null,null,null,null,null,null,null,null,"b".repeat(201),null));
    }
}
