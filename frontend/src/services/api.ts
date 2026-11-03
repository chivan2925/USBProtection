import type { Device, Endpoint, Snapshot } from './models.ts'
import type { WhitelistEntry } from '../types/policy.ts'
import type { EventPage, EventQuery } from '../types/event.ts'
import { clearCsrfToken, readObject, readText, refreshCsrfToken, request } from './http.ts'
import { mapEndpoint } from './mappers/endpointMapper.ts'
import { mapWhitelistEntry } from './mappers/policyMapper.ts'
import { mapEventPage } from './mappers/eventMapper.ts'

export class BackendUnavailableError extends Error {
  constructor() {
    super('Backend hiện chưa cung cấp API cho chức năng này.')
    this.name = 'BackendUnavailableError'
  }
}

export const capabilities = Object.freeze({
  monitoring: true, authentication: true, events: true,
  allow: true, block: false, revoke: true, settings: false,
})

export interface AdminUser { username: string }
export interface AllowRequest { usbDeviceId: number }
export interface DeviceQuery { page: number; size: number; endpointId?: string; status?: Device['status']; q?: string }
export interface DevicePage { content: Device[]; page: number; size: number; totalElements: number; totalPages: number }

function endpointPath(endpointId: string) {
  if (!endpointId.trim()) throw new Error('Cần chọn endpoint trước khi thay đổi whitelist.')
  return `/api/admin/endpoints/${encodeURIComponent(endpointId)}`
}

function integer(value: unknown, name: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0)
    throw new Error(`API trả ${name} không hợp lệ.`)
  return value
}

function mapDeviceRow(value: unknown): Device {
  const row = readObject(value)
  const id = readText(row.id, true)!
  const endpointId = readText(row.endpointId, true)!
  const status = row.status
  if (status !== 'allowed' && status !== 'blocked' && status !== 'detected' && status !== 'disconnected')
    throw new Error('API trả trạng thái USB không hợp lệ.')
  const detectedAt = readText(row.detectedAt, true)!
  if (Number.isNaN(Date.parse(detectedAt))) throw new Error('API trả thời gian USB không hợp lệ.')
  const usbDeviceId = integer(row.usbDeviceId, 'usbDeviceId')
  if (!usbDeviceId) throw new Error('API trả usbDeviceId không hợp lệ.')
  return {
    id, endpointId, usbDeviceId, status,
    name: readText(row.name, true)!, hash: readText(row.hash),
    endpointName: readText(row.endpointName), vendorId: readText(row.vendorId),
    productId: readText(row.productId), serialNumber: readText(row.serialNumber),
    detectedAt, blockedAt: readText(row.blockedAt), blockReason: readText(row.blockReason),
  }
}

function mapDevicePage(value: unknown): DevicePage {
  const page = readObject(value)
  const pageNumber = integer(page.page, 'device.page')
  const size = integer(page.size, 'device.size')
  const totalElements = integer(page.totalElements, 'device.totalElements')
  const totalPages = integer(page.totalPages, 'device.totalPages')
  if (size < 1 || size > 100 || totalPages !== Math.ceil(totalElements / size) || !Array.isArray(page.content))
    throw new Error('API trả phân trang thiết bị không hợp lệ.')
  const content = page.content.map(mapDeviceRow)
  if (content.length > size || content.length > Math.max(0, totalElements - pageNumber * size) ||
      new Set(content.map(item => item.id)).size !== content.length)
    throw new Error('API trả danh sách USB không hợp lệ.')
  return { content, page: pageNumber, size, totalElements, totalPages }
}

function mapSummary(value: unknown) {
  const data = readObject(value)
  return {
    totalDevices: integer(data.totalDevices, 'totalDevices'),
    blockedDevices: integer(data.blockedDevices, 'blockedDevices'),
    allowedDevices: integer(data.allowedDevices, 'allowedDevices'),
    recentEvents: integer(data.recentEvents, 'recentEvents'),
    blockedToday: integer(data.blockedToday, 'blockedToday'),
  }
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

  async getDevicePage(query: DeviceQuery, signal: AbortSignal): Promise<DevicePage> {
    signal.throwIfAborted()
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(query)) if (value !== undefined && value !== '') params.set(key, String(value))
    return mapDevicePage(await request(`/api/admin/device-states?${params}`, { signal }))
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
    await request(`${endpointPath(endpointId)}/whitelist`, { method: 'POST', body: { usbDeviceId: body.usbDeviceId } })
  },

  async revokeOnEndpoint(endpointId: string, entryId: string) {
    if (!entryId.trim()) throw new Error('Thiếu entryId cần thu hồi.')
    await request(`${endpointPath(endpointId)}/whitelist/${encodeURIComponent(entryId)}`, { method: 'DELETE' })
  },

  // No multi-page audit scan. The page is a bounded preview; /events owns history pagination.
  // The device inventory is independently paginated; /devices owns global browsing.
  async getSnapshot(signal: AbortSignal, selectedEndpointId?: string, route?: string): Promise<Snapshot> {
    signal.throwIfAborted()
    const data = await request('/api/admin/endpoints', { signal })
    if (!Array.isArray(data)) throw new Error('API endpoints phải trả danh sách.')
    const endpoints = data.map(mapEndpoint)
    if (new Set(endpoints.map(endpoint => endpoint.id)).size !== endpoints.length)
      throw new Error('API trả endpoint ID bị trùng.')
    if (selectedEndpointId) {
      const detail = await protectionApi.getEndpoint(selectedEndpointId, signal)
      const index = endpoints.findIndex(endpoint => endpoint.id === selectedEndpointId)
      if (index < 0) endpoints.push(detail)
      else endpoints[index] = detail
    }
    const recent = await protectionApi.getEventPage({ page: 0, size: 100, ...(selectedEndpointId ? { endpointId: selectedEndpointId } : {}) }, signal)
    const events = recent.content
    if (new Set(events.map(event => event.id)).size !== events.length)
      throw new Error('API trả event ID bị trùng trong trang.')

    const startOfToday = new Date()
    startOfToday.setHours(0, 0, 0, 0)
    const endOfToday = new Date(startOfToday)
    endOfToday.setDate(endOfToday.getDate() + 1)
    const dates = new URLSearchParams({ from: startOfToday.toISOString(), to: endOfToday.toISOString() })
    const summary = mapSummary(await request(`/api/admin/dashboard/summary?${dates}`, { signal }))

    const activeRoute = route ?? (typeof window === 'undefined' ? undefined : window.location.hash.replace(/^#/, '').split('?')[0])
    const devices: Device[] = selectedEndpointId
      ? (await protectionApi.getDevicePage({ page: 0, size: 100, endpointId: selectedEndpointId }, signal)).content
      : []
    const whitelist: WhitelistEntry[] = []
    // Only read rules for the active view, not N HTTP calls on every dashboard refresh.
    const whitelistIds = selectedEndpointId ? [selectedEndpointId] : activeRoute === undefined || activeRoute === '/whitelist'
      ? endpoints.map(endpoint => endpoint.id) : []
    for (const endpointId of whitelistIds) whitelist.push(...await protectionApi.getWhitelist(endpointId, signal))
    return {
      endpoints, events, devices, whitelist,
      overview: {
        protection: 'unknown', service: 'unknown', ...summary,
        onlineEndpoints: endpoints.filter(endpoint => endpoint.status === 'online').length,
        offlineEndpoints: endpoints.filter(endpoint => endpoint.status === 'offline').length,
        unknownEndpoints: endpoints.filter(endpoint => endpoint.status === 'unknown').length,
      },
    }
  },
}
