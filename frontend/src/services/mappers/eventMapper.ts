import type { EventDevice, UsbEvent } from '../../types/event.ts'

// Draft read DTO: M1 must freeze the shape before M4 enables live reads.
// Fetching, authentication and pagination belong to M4's shared adapter.
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

function timestamp(value: unknown): string {
  const result = text(value, 'occurredAt', true)!
  const parts = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.exec(result)
  if (!parts || Number.isNaN(Date.parse(result))) throw new Error('Invalid event payload: occurredAt must include a timezone')
  const [, year, month, day, hour, minute, second] = parts.map(Number)
  const calendar = new Date(0)
  calendar.setUTCFullYear(year, month - 1, day)
  if (calendar.getUTCFullYear() !== year || calendar.getUTCMonth() !== month - 1 || calendar.getUTCDate() !== day || hour > 23 || minute > 59 || second > 59) throw new Error('Invalid event payload: occurredAt is not a valid date')
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
    interfaceClass: text(device.interface, 'device.interface'),
  }
}

export function mapEvent(value: unknown): UsbEvent {
  const event = record(value, 'event')
  let id: string
  if (typeof event.id === 'number' && Number.isSafeInteger(event.id) && event.id > 0) id = String(event.id)
  else id = text(event.id, 'id', true)!
  // Other demo event categories need agreement with M1, never guessing.
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
    username: username?.trim() ? username : 'UNKNOWN',
    type,
    decision,
    deviceName: device?.name,
    message: text(event.message, 'message') ?? '',
    linuxUid: integer(event.linuxUid, 'linuxUid'),
    sessionId: text(event.sessionId, 'sessionId'),
    sessionType: text(event.sessionType, 'sessionType'),
    seat: text(event.seat, 'seat'),
    device,
  }
}

// M4 unwraps the agreed page envelope; an invalid item fails the whole read.
export function mapEvents(value: unknown): UsbEvent[] {
  if (!Array.isArray(value)) throw new Error('Invalid event payload: expected event array')
  return value.map(mapEvent)
}
