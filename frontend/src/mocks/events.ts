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
]
