import type { EventFilterValues, UsbEvent } from '../types/event'
import { matches } from './format.ts'

function localMidnight(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return undefined
  const actual = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return actual === value ? date : undefined
}

// Inclusive local calendar dates become [from midnight, day-after-to midnight).
// Advance the calendar day rather than adding 24 hours, including across DST.
export function getEventDateRange(filters: Pick<EventFilterValues, 'dateFrom' | 'dateTo'>) {
  const from = filters.dateFrom ? localMidnight(filters.dateFrom) : undefined
  const to = filters.dateTo ? localMidnight(filters.dateTo) : undefined
  if ((filters.dateFrom && !from) || (filters.dateTo && !to) || (from && to && from > to)) return { valid: false }
  if (to) to.setDate(to.getDate() + 1)
  return { valid: true, fromInclusive: from?.toISOString(), toExclusive: to?.toISOString() }
}

export function matchesEventFilters(event: UsbEvent, filters: EventFilterValues) {
  const decision = event.decision ?? (event.type === 'allowed' || event.type === 'blocked' ? event.type : '')
  const range = getEventDateRange(filters)
  const time = Date.parse(event.timestamp)
  const hasDateFilter = Boolean(filters.dateFrom || filters.dateTo)
  return range.valid &&
    matches(filters.endpoint, event.endpointName, event.endpointId) &&
    matches(filters.username, event.username) &&
    matches(filters.device, event.deviceName, event.device?.vendorId, event.device?.productId, event.device?.serial, event.device?.hash) &&
    (!filters.eventType || event.type === filters.eventType) &&
    (!filters.decision || (filters.decision === 'none' ? !decision : decision === filters.decision)) &&
    (!hasDateFilter || (!Number.isNaN(time) &&
      (!range.fromInclusive || time >= Date.parse(range.fromInclusive)) &&
      (!range.toExclusive || time < Date.parse(range.toExclusive))))
}

