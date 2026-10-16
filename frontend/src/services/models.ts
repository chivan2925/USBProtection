// Frontend view models; services/mappers translates backend DTOs into these fields.
export type DeviceStatus = 'allowed' | 'blocked' | 'detected' | 'disconnected'
import type { UsbEvent } from '../types/event'
import type { WhitelistEntry } from '../types/policy'
export type { UsbEvent, EventType } from '../types/event'
export interface Device {
  id: string
  usbDeviceId?: number
  hash?: string
  name: string
  vendorId?: string
  productId?: string
  serialNumber?: string
  endpointId?: string
  endpointName?: string
  status: DeviceStatus
  detectedAt?: string
  blockedAt?: string
  blockReason?: string
}
export interface Endpoint {
  id: string
  name: string
  hostname?: string
  ipAddress?: string
  operatingSystem?: string
  status: 'online' | 'offline' | 'unknown'
  currentUser?: string
  lastSeenAt?: string
  policyVersion?: string
  appliedPolicyVersion?: string
}
export interface Overview {
  protection: 'enabled' | 'disabled' | 'unknown'
  service: 'running' | 'stopped' | 'unknown'
  totalDevices: number | null
  blockedDevices: number | null
  allowedDevices: number | null
  recentEvents: number | null
  onlineEndpoints: number | null
  offlineEndpoints: number | null
  unknownEndpoints?: number
  blockedToday?: number
}
export interface Snapshot {
  overview: Overview
  devices: Device[]
  endpoints: Endpoint[]
  events: UsbEvent[]
  whitelist: WhitelistEntry[]
}
export type DeviceAction = 'allow' | 'block' | 'revoke'
