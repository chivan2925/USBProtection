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
