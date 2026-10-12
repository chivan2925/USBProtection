import type { EventFilterValues, UsbEvent } from '../types/event'
import { matches } from './format.ts'
export function matchesEventFilters(event: UsbEvent, filters: EventFilterValues) {
  const decision = event.decision ?? (event.type === 'allowed' || event.type === 'blocked' ? event.type : '')
  const time = new Date(event.timestamp)
  const localDate = Number.isNaN(time.getTime()) ? '' : `${time.getFullYear()}-${String(time.getMonth() + 1).padStart(2, '0')}-${String(time.getDate()).padStart(2, '0')}`
  return matches(filters.endpoint, event.endpointName, event.endpointId) && matches(filters.username, event.username) && matches(filters.device, event.deviceName) && (!filters.decision || (filters.decision === 'none' ? !decision : decision === filters.decision)) && (!filters.date || filters.date === localDate)
}

