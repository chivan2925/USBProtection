// Frontend view model; transport field names are mapped when a backend API is available.
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
  device?: EventDevice
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
