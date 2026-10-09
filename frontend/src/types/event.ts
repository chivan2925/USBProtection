<<<<<<< HEAD
// M5 owner — event frontend types
// Must stay in sync with M1 AgentEventRequest contract.

export type EventType = 'connected' | 'disconnected' | 'blocked' | 'allowed' | 'service'
export type Decision = 'ALLOWED' | 'BLOCKED' | 'UNKNOWN'

export interface UsbEventDevice {
  vendorId: string
  productId: string
  serial?: string
  name?: string
  hash?: string
  interface?: string
}

export interface UsbEvent {
  id: string
  occurredAt: string
  endpointId?: string
  hostname?: string
  linuxUsername?: string
  device?: UsbEventDevice
  eventType: EventType
  decision: Decision
  message?: string
}
=======
﻿// Frontend view model; transport field names are mapped when a backend API is available.
export type EventType = 'connected' | 'disconnected' | 'blocked' | 'allowed' | 'service'
export interface UsbEvent {
  id: string
  type: EventType
  timestamp: string
  decision?: 'allowed' | 'blocked'
  deviceName?: string
  endpointId?: string
  endpointName?: string
  username?: string
  message: string
}
export interface EventFilterValues {
  endpoint: string
  username: string
  device: string
  decision: string
  date: string
}
export const emptyEventFilters: EventFilterValues = { endpoint: '', username: '', device: '', decision: '', date: '' }
>>>>>>> origin/huy
