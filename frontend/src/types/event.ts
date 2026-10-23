// Frontend view model mapped from the Admin API v0.2 at the adapter boundary.
// Mapping and integration limits: docs/qa/EVENT_VIEW_MODEL_MAPPING.md.
export type EventType = 'connected' | 'disconnected' | 'blocked' | 'allowed' | 'service'
export type EventDecision = 'allowed' | 'blocked' | 'unknown'
export interface EventDevice {
  name?: string
  vendorId?: string
  productId?: string
  serial?: string
  hash?: string
  interfaceClass?: string
}
export interface UsbEvent {
  id: string
  type: EventType
  timestamp: string
  receivedAt?: string
  decision?: EventDecision
  deviceName?: string
  endpointId?: string
  endpointName?: string
  username?: string
  message: string
  linuxUid?: number
  sessionId?: string
  sessionType?: string
  seat?: string
  usbDeviceId?: string
  device?: EventDevice
}
// Zero-based server page. Content is only this page, not the full event history.
export interface EventPage {
  content: UsbEvent[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
export interface EventQuery {
  page: number
  size: number
  endpoint?: string
  device?: string
  username?: string
  endpointId?: string
  linuxUsername?: string
  deviceId?: string
  decision?: 'ALLOWED' | 'BLOCKED'
  eventType?: 'CONNECTED' | 'DISCONNECTED'
  from?: string
  to?: string
}
export interface EventFilterValues {
  endpoint: string
  username: string
  device: string
  decision: string
  eventType: string
  dateFrom: string
  dateTo: string
}
export const emptyEventFilters: EventFilterValues = { endpoint: '', username: '', device: '', decision: '', eventType: '', dateFrom: '', dateTo: '' }
