// Frontend view model; transport field names are mapped when a backend API is available.
// Mapping and integration limits: docs/qa/EVENT_VIEW_MODEL_MAPPING.md.
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
