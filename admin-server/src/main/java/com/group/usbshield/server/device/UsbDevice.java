package com.group.usbshield.server.device;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "usb_devices", indexes = {
    @Index(name = "idx_usb_hash", columnList = "deviceHash"),
    @Index(name = "idx_usb_vid_pid", columnList = "vendorId, productId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsbDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String vendorId;

    @Column(nullable = false, length = 10)
    private String productId;

    @Column(length = 150)
    private String serialNumber;

    @Column(length = 255)
    private String deviceHash;

    @Column(length = 255)
    private String deviceName;

    @Column(name = "device_interfaces", length = 100)
    private String interfaces;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
