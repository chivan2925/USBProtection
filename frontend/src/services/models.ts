// View models for the frontend. These are not an existing backend API contract.
export type DeviceStatus = 'allowed' | 'blocked' | 'detected' | 'disconnected'
export type EventType = 'connected' | 'disconnected' | 'blocked' | 'allowed' | 'service'
export interface Device {
  id: string
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
  operatingSystem?: string
  status: 'online' | 'offline' | 'unknown'
  currentUser?: string
  lastSeenAt?: string
  policyVersion?: string
}
export interface UsbEvent {
  id: string
  type: EventType
  timestamp: string
  deviceName?: string
  endpointId?: string
  endpointName?: string
  username?: string
  message: string
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
}
export interface Snapshot {
  overview: Overview
  devices: Device[]
  endpoints: Endpoint[]
  events: UsbEvent[]
}
export type DeviceAction = 'allow' | 'block' | 'revoke'
