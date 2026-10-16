import type { EventDevice, EventPage, UsbEvent } from '../../types/event.ts'

// Admin EventResponse/EventPage: docs/API.md (v0.2).
// Fetching, authentication and page/query orchestration belong to M4's adapter.
function record(value: unknown, field: string): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`Invalid event payload: ${field}`)
  return value as Record<string, unknown>
}

function text(value: unknown, field: string, required = false): string | undefined {
  if (value == null && !required) return undefined
  if (typeof value !== 'string' || (required && !value.trim())) throw new Error(`Invalid event payload: ${field}`)
  return value
}

function integer(value: unknown, field: string): number | undefined {
  if (value == null) return undefined
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error(`Invalid event payload: ${field}`)
  return value
}

function identifier(value: unknown, field: string): string {
  if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) return String(value)
  if (typeof value === 'string' && /^[1-9]\d*$/.test(value)) return value
  throw new Error(`Invalid event payload: ${field} must be a positive integer ID`)
}

function pageInteger(value: unknown, field: string, min = 0, max = Number.MAX_SAFE_INTEGER): number {
  const result = integer(value, field)
  if (result == null || result < min || result > max) throw new Error(`Invalid event payload: ${field}`)
  return result
}

function timestamp(value: unknown, field = 'occurredAt'): string {
  const result = text(value, field, true)!
  const parts = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.exec(result)
  if (!parts || Number.isNaN(Date.parse(result))) throw new Error(`Invalid event payload: ${field} must include a timezone`)
  const [, year, month, day, hour, minute, second] = parts.map(Number)
  const calendar = new Date(0)
  calendar.setUTCFullYear(year, month - 1, day)
  if (calendar.getUTCFullYear() !== year || calendar.getUTCMonth() !== month - 1 || calendar.getUTCDate() !== day || hour > 23 || minute > 59 || second > 59) throw new Error(`Invalid event payload: ${field} is not a valid date`)
  return result
}

function mapDevice(value: unknown): EventDevice | undefined {
  if (value == null) return undefined
  const device = record(value, 'device')
  return {
    name: text(device.name, 'device.name'),
    vendorId: text(device.vendorId, 'device.vendorId'),
    productId: text(device.productId, 'device.productId'),
    serial: text(device.serial, 'device.serial'),
    hash: text(device.hash, 'device.hash'),
    interfaceClass: text(device.interfaceClass, 'device.interfaceClass'),
  }
}

export function mapEvent(value: unknown): UsbEvent {
  const event = record(value, 'event')
  const id = identifier(event.id, 'id')
  // Other demo event categories are not Admin API event types.
  const type = event.eventType === 'CONNECTED' ? 'connected' : event.eventType === 'DISCONNECTED' ? 'disconnected' : undefined
  if (!type) throw new Error('Invalid event payload: unsupported eventType')
  const decision = event.decision === 'ALLOWED' ? 'allowed' : event.decision === 'BLOCKED' ? 'blocked' : event.decision === 'UNKNOWN' ? 'unknown' : undefined
  if (!decision) throw new Error('Invalid event payload: unsupported decision')
  const username = text(event.linuxUsername, 'linuxUsername')
  const device = mapDevice(event.device)
  return {
    id,
    endpointId: text(event.endpointId, 'endpointId', true)!,
    endpointName: text(event.hostname, 'hostname'),
    timestamp: timestamp(event.occurredAt),
    receivedAt: event.receivedAt == null ? undefined : timestamp(event.receivedAt, 'receivedAt'),
    username: username?.trim() ? username : 'UNKNOWN',
    type,
    decision,
    deviceName: device?.name,
    message: text(event.message, 'message') ?? '',
    linuxUid: integer(event.linuxUid, 'linuxUid'),
    sessionId: text(event.sessionId, 'sessionId'),
    sessionType: text(event.sessionType, 'sessionType'),
    seat: text(event.seat, 'seat'),
    usbDeviceId: event.usbDeviceId == null ? undefined : identifier(event.usbDeviceId, 'usbDeviceId'),
    device,
  }
}

// Array-only helper; an invalid item fails the whole read.
export function mapEvents(value: unknown): UsbEvent[] {
  if (!Array.isArray(value)) throw new Error('Invalid event payload: expected event array')
  return value.map(mapEvent)
}

// Preserve server order and totals; never filter, sort or paginate one page locally.
export function mapEventPage(value: unknown): EventPage {
  const envelope = record(value, 'page response')
  const page = pageInteger(envelope.page, 'page')
  const size = pageInteger(envelope.size, 'size', 1, 100)
  const totalElements = pageInteger(envelope.totalElements, 'totalElements')
  const totalPages = pageInteger(envelope.totalPages, 'totalPages')
  const content = mapEvents(envelope.content)
  if (totalPages !== Math.ceil(totalElements / size) || content.length > size || content.length > totalElements || (page >= totalPages && content.length > 0)) {
    throw new Error('Invalid event payload: inconsistent page metadata')
  }
  return { content, page, size, totalElements, totalPages }
}
