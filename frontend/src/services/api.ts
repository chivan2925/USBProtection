import type { Device, Endpoint, Snapshot, UsbEvent } from './models.ts'
import type { WhitelistEntry } from '../types/policy.ts'
import type { EventPage, EventQuery } from '../types/event.ts'
import { clearCsrfToken, readId, readObject, readText, refreshCsrfToken, request } from './http.ts'
import { mapEndpoint } from './mappers/endpointMapper.ts'
import { mapWhitelistEntry } from './mappers/policyMapper.ts'
import { mapEventPage, mapEvents } from './mappers/eventMapper.ts'

export class BackendUnavailableError extends Error {
  constructor() {
    super('Backend hiện chưa cung cấp API cho chức năng này.')
    this.name = 'BackendUnavailableError'
  }
}

// Flags describe implemented adapters, not server availability or USB state.
export const capabilities = Object.freeze({
  monitoring: true,
  authentication: true,
  events: true,
  allow: true,
  block: false,
  revoke: true,
  settings: false,
})

export interface AdminUser { username: string }
export interface AllowRequest { usbDeviceId: number }

function endpointPath(endpointId: string) {
  if (!endpointId.trim()) throw new Error('Cần chọn endpoint trước khi thay đổi whitelist.')
  return `/api/admin/endpoints/${encodeURIComponent(endpointId)}`
}

// Dashboard and management snapshots need all events before deriving metrics.
async function getAllRows(path: string, signal: AbortSignal): Promise<unknown[]> {
  const rows: unknown[] = []
  let totalPages: number | undefined
  let totalElements: number | undefined
  for (let page = 0; page < 100; page++) {
    const data = await request(`${path}?page=${page}&size=100`, { signal })
    if (Array.isArray(data)) {
      if (page !== 0) throw new Error('API thay đổi dạng phân trang giữa các request.')
      return data
    }
    const result = readObject(data)
    if (!Array.isArray(result.content) || result.page !== page ||
        typeof result.totalPages !== 'number' || !Number.isSafeInteger(result.totalPages) || result.totalPages < 0 ||
        typeof result.totalElements !== 'number' || !Number.isSafeInteger(result.totalElements) || result.totalElements < 0) {
      throw new Error('API trả phân trang không hợp lệ. Cần chốt contract với M1.')
    }
    if (page === 0) { totalPages = result.totalPages; totalElements = result.totalElements }
    if (result.totalPages !== totalPages || result.totalElements !== totalElements) {
      throw new Error('Dữ liệu thay đổi trong lúc tải các trang. Hãy tải lại để có danh sách đầy đủ.')
    }
    if (result.totalPages === 0 && result.totalElements !== 0) throw new Error('API trả tổng số trang không hợp lệ.')
    if (result.content.length === 0 && page < result.totalPages - 1) throw new Error('API thiếu dữ liệu trong trang.')
    rows.push(...result.content)
    if (page >= result.totalPages - 1) {
      if (rows.length !== totalElements) throw new Error('API trả thiếu hoặc thừa dữ liệu phân trang.')
      return rows
    }
  }
  throw new Error('Dữ liệu vượt quá 100 trang. Cần API lọc/phân trang server trước khi hiển thị đầy đủ.')
}

function buildDevices(events: UsbEvent[], deviceIds: Map<string, number>): Device[] {
  const devices: Device[] = []
  const seen = new Set<string>()
  const sorted = [...events].sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))
  for (const event of sorted) {
    const usb = event.device
    if (!usb || !event.endpointId) continue
    // Names are labels, never fingerprints.
    let identity = usb.hash
    if (!identity && usb.serial) identity = `${usb.vendorId}:${usb.productId}:${usb.serial}`
    if (!identity) identity = `event:${event.id}`
    const id = `${event.endpointId}:${identity}`
    if (seen.has(id)) continue
    seen.add(id)
    let status: Device['status'] = 'detected'
    if (event.decision === 'allowed') status = 'allowed'
    if (event.decision === 'blocked') status = 'blocked'
    if (event.type === 'disconnected') status = 'disconnected'
    devices.push({
      id, usbDeviceId: deviceIds.get(event.id), hash: usb.hash,
      name: usb.name ?? 'USB chưa có tên', endpointId: event.endpointId,
      endpointName: event.endpointName, vendorId: usb.vendorId, productId: usb.productId,
      serialNumber: usb.serial, status, detectedAt: event.timestamp,
      blockedAt: event.decision === 'blocked' ? event.timestamp : undefined,
      blockReason: event.decision === 'blocked' ? 'Quyết định BLOCKED trong sự kiện gần nhất.' : undefined,
    })
  }
  return devices
}

export const protectionApi = {
  async getCurrentAdmin(signal?: AbortSignal): Promise<AdminUser> {
    const data = readObject(await request('/api/auth/me', { signal, notifyUnauthorized: false }))
    await refreshCsrfToken(signal)
    return { username: readText(data.username, true)! }
  },

  async login(username: string, password: string): Promise<AdminUser> {
    await refreshCsrfToken()
    await request('/api/auth/login', { method: 'POST', body: { username, password }, notifyUnauthorized: false })
    return protectionApi.getCurrentAdmin()
  },

  async logout() {
    await request('/api/auth/logout', { method: 'POST' })
    clearCsrfToken()
  },

  async getEventPage(query: EventQuery, signal: AbortSignal): Promise<EventPage> {
    signal.throwIfAborted()
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(query)) if (value !== undefined) params.set(key, String(value))
    return mapEventPage(await request(`/api/admin/events?${params}`, { signal }))
  },

  async getEndpoint(endpointId: string, signal: AbortSignal): Promise<Endpoint> {
    const endpoint = mapEndpoint(await request(endpointPath(endpointId), { signal }))
    if (endpoint.id !== endpointId) throw new Error('Server trả chi tiết của endpoint khác.')
    return endpoint
  },

  async getWhitelist(endpointId: string, signal: AbortSignal): Promise<WhitelistEntry[]> {
    const data = await request(`${endpointPath(endpointId)}/whitelist`, { signal })
    if (!Array.isArray(data)) throw new Error('API whitelist phải trả danh sách entries.')
    const entries: WhitelistEntry[] = []
    for (const value of data) {
      const entry = readObject(value)
      if (entry.enabled === false) continue
      entries.push(mapWhitelistEntry(entry, endpointId))
    }
    return entries
  },

  async allowOnEndpoint(endpointId: string, body: AllowRequest) {
    if (!Number.isSafeInteger(body.usbDeviceId) || body.usbDeviceId <= 0) throw new Error('Cần chọn usbDeviceId hợp lệ từ sự kiện backend.')
    await request(`${endpointPath(endpointId)}/whitelist`, {
      method: 'POST', body: { usbDeviceId: body.usbDeviceId },
    })
  },

  async revokeOnEndpoint(endpointId: string, entryId: string) {
    if (!entryId.trim()) throw new Error('Thiếu entryId cần thu hồi.')
    await request(`${endpointPath(endpointId)}/whitelist/${encodeURIComponent(entryId)}`, { method: 'DELETE' })
  },

  async getSnapshot(signal: AbortSignal, selectedEndpointId?: string): Promise<Snapshot> {
    signal.throwIfAborted()
    const endpoints = (await getAllRows('/api/admin/endpoints', signal)).map(mapEndpoint)
    if (new Set(endpoints.map(endpoint => endpoint.id)).size !== endpoints.length) throw new Error('API trả endpoint ID bị trùng.')
    if (selectedEndpointId) {
      const detail = await protectionApi.getEndpoint(selectedEndpointId, signal)
      const index = endpoints.findIndex(endpoint => endpoint.id === selectedEndpointId)
      if (index < 0) endpoints.push(detail)
      else endpoints[index] = detail
    }
    const eventRows = await getAllRows('/api/admin/events', signal)
    const events = mapEvents(eventRows)
    const deviceIds = new Map<string, number>()
    for (const value of eventRows) {
      const row = readObject(value)
      if (row.usbDeviceId == null) continue
      if (!Number.isSafeInteger(row.usbDeviceId) || (row.usbDeviceId as number) <= 0) throw new Error('API trả usbDeviceId không hợp lệ.')
      deviceIds.set(readId(row.id), row.usbDeviceId as number)
    }
    if (new Set(events.map(event => event.id)).size !== events.length) throw new Error('API trả event ID bị trùng giữa các trang. Hãy tải lại.')
    const whitelist: WhitelistEntry[] = []
    for (const endpoint of endpoints) {
      whitelist.push(...await protectionApi.getWhitelist(endpoint.id, signal))
    }
    const devices = buildDevices(events, deviceIds)
    const startOfToday = new Date()
    startOfToday.setHours(0, 0, 0, 0)
    const endOfToday = new Date(startOfToday)
    endOfToday.setDate(endOfToday.getDate() + 1)
    return {
      endpoints, events, devices, whitelist,
      overview: {
        protection: 'unknown', service: 'unknown',
        totalDevices: devices.length, blockedDevices: devices.filter(device => device.status === 'blocked').length,
        allowedDevices: devices.filter(device => device.status === 'allowed').length,
        recentEvents: events.length,
        blockedToday: events.filter(event => event.decision === 'blocked' && event.type === 'connected' &&
          Date.parse(event.timestamp) >= startOfToday.getTime() && Date.parse(event.timestamp) < endOfToday.getTime()).length,
        onlineEndpoints: endpoints.filter(endpoint => endpoint.status === 'online').length,
        offlineEndpoints: endpoints.filter(endpoint => endpoint.status === 'offline').length,
        unknownEndpoints: endpoints.filter(endpoint => endpoint.status === 'unknown').length,
      },
    }
  },
}
