package com.group.usbshield.server.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsbDeviceRepository extends JpaRepository<UsbDevice, Long> {
    Optional<UsbDevice> findByDeviceHash(String deviceHash);
    Optional<UsbDevice> findByVendorIdAndProductIdAndSerialNumber(String vendorId, String productId, String serialNumber);
}
