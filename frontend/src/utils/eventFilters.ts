import type { EventFilterValues, UsbEvent } from '../types/event'
import { matches } from './format.ts'
import type { EventQuery } from '../types/event.ts'

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

// Inclusive Java Instant upper bound includes sub-millisecond events on the last
// selected day, while excluding midnight at the start of the following day.
export function getEventQuery(filters: EventFilterValues, page = 0, size = 20): EventQuery {
  const range = getEventDateRange(filters)
  if (!range.valid) throw new Error('Khoảng ngày không hợp lệ. Kiểm tra ngày bắt đầu và kết thúc.')
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) throw new Error('Trang hoặc kích thước trang không hợp lệ.')
  const deviceId = filters.device.trim()
  if (deviceId && (!/^[1-9]\d*$/.test(deviceId) || BigInt(deviceId) > 9223372036854775807n)) throw new Error('Device ID phải là số nguyên dương hợp lệ.')
  if (filters.decision && !['allowed', 'blocked'].includes(filters.decision)) throw new Error('API chỉ hỗ trợ lọc quyết định ALLOW hoặc BLOCK.')
  if (filters.eventType && !['connected', 'disconnected'].includes(filters.eventType)) throw new Error('API chỉ hỗ trợ sự kiện cắm hoặc tháo USB.')
  return {
    page, size,
    endpointId: filters.endpoint.trim() || undefined,
    linuxUsername: filters.username.trim() || undefined,
    deviceId: deviceId || undefined,
    decision: filters.decision ? filters.decision === 'allowed' ? 'ALLOWED' : 'BLOCKED' : undefined,
    eventType: filters.eventType ? filters.eventType === 'connected' ? 'CONNECTED' : 'DISCONNECTED' : undefined,
    from: range.fromInclusive,
    to: range.toExclusive ? new Date(Date.parse(range.toExclusive) - 1000).toISOString().replace(/\.\d{3}Z$/, '.999999999Z') : undefined,
  }
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

