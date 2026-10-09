<<<<<<< HEAD
// M5 owner — mock event data for Week 1 UI.
// Shows Endpoint + Linux User + USB + Decision columns.
// NOTE: this is UI mock data only, NOT a real central event.

import type { UsbEvent } from '../types/event'

export const mockEvents: UsbEvent[] = [
  {
    id: 'evt-001',
    occurredAt: '2026-09-28T14:10:16+07:00',
    endpointId: 'POC-LAB-PC-01',
    hostname: 'lab-pc-01.local',
    linuxUsername: 'student01',
    device: {
      vendorId: '0951',
      productId: '1665',
      serial: 'C81F660E8BE8FFA14601FEF6',
      name: 'Kingston DataTraveler 2.0',
      interface: '08:06:50',
    },
    eventType: 'connected',
    decision: 'BLOCKED',
    message: 'Unknown USB Mass Storage blocked',
  },
  {
    id: 'evt-002',
    occurredAt: '2026-09-28T10:05:00+07:00',
    endpointId: 'POC-LAB-PC-02',
    hostname: 'lab-pc-02.local',
    linuxUsername: 'employee07',
    device: {
      vendorId: '0781',
      productId: '5583',
      serial: 'SD-4F2291',
      name: 'SanDisk Ultra',
      interface: '08:06:50',
    },
    eventType: 'connected',
    decision: 'ALLOWED',
    message: 'Whitelisted USB Mass Storage allowed',
  },
  {
    id: 'evt-003',
    occurredAt: '2026-09-27T17:45:00+07:00',
    endpointId: 'POC-LAB-PC-01',
    hostname: 'lab-pc-01.local',
    linuxUsername: 'usbdev',
    device: {
      vendorId: '093a',
      productId: '2510',
      name: 'USB Optical Mouse',
      interface: '03:01:02',
    },
    eventType: 'connected',
    decision: 'ALLOWED',
    message: 'HID device (class 03) — not Mass Storage',
  },
  {
    id: 'evt-004',
    occurredAt: '2026-09-27T09:00:00+07:00',
    endpointId: 'POC-LAB-PC-02',
    hostname: 'lab-pc-02.local',
    linuxUsername: 'student01',
    device: {
      vendorId: '0951',
      productId: '1665',
      serial: 'C81F660E8BE8FFA14601FEF6',
      name: 'Kingston DataTraveler 2.0',
      interface: '08:06:50',
    },
    eventType: 'disconnected',
    decision: 'UNKNOWN',
    message: 'Device disconnected',
  },
=======
﻿import type { UsbEvent } from '../services/models'

export const mockEvents: UsbEvent[] = [
  { id: 'mock-1', timestamp: '2026-10-05T10:30:00+07:00', endpointName: 'LAB-PC-02', username: 'employee07', deviceName: 'SanDisk Ultra 32GB', type: 'connected', decision: 'allowed', message: 'USB trong whitelist được phép kết nối.' },
  { id: 'mock-2', timestamp: '2026-10-05T10:25:00+07:00', endpointName: 'LAB-PC-01', username: 'student01', deviceName: 'Kingston DataTraveler 64GB', type: 'connected', decision: 'blocked', message: 'USB ngoài whitelist bị chặn.' },
  { id: 'mock-3', timestamp: '2026-10-05T10:20:00+07:00', endpointName: 'linux-lab-01', username: 'huy', deviceName: 'Logitech USB Keyboard', type: 'allowed', decision: 'allowed', message: 'Bàn phím USB được cho phép.' },
  { id: 'mock-4', timestamp: '2026-10-05T10:15:00+07:00', endpointName: 'LAB-PC-02', username: 'employee07', deviceName: 'SanDisk Ultra 32GB', type: 'disconnected', message: 'USB đã được tháo khỏi máy.' },
  { id: 'mock-5', timestamp: '2026-10-05T10:10:00+07:00', endpointName: 'linux-lab-02', username: 'an', deviceName: 'Samsung Portable SSD T7', type: 'blocked', decision: 'blocked', message: 'Thiết bị lưu trữ bị chặn theo chính sách.' },
  { id: 'mock-6', timestamp: '2026-10-05T10:05:00+07:00', endpointName: 'linux-lab-01', username: 'root', type: 'service', message: 'Dịch vụ USBGuard đã khởi động.' },
>>>>>>> origin/huy
]
