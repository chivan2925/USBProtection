import type { DeviceAction, Snapshot } from './models'

import type { EventPage, EventQuery } from '../types/event.ts'
import { mapEventPage } from './mappers/eventMapper.ts'

export interface AdminSession { username: string }
export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message?: string) {
    super(message ?? (status === 401 ? 'Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập.' : status === 403 ? 'Bạn không có quyền truy cập hoặc phiên bảo mật cần được làm mới.' : status === 400 ? 'Bộ lọc hoặc dữ liệu yêu cầu không hợp lệ.' : 'Máy chủ chưa xử lý được yêu cầu. Vui lòng thử lại.'))
    this.name = 'ApiError'
    this.status = status
  }
}

async function requestJson(path: string, options: RequestInit = {}): Promise<unknown> {
  options.signal?.throwIfAborted()
  const response = await fetch(path, { ...options, credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json', ...options.headers } })
  if (!response.ok) throw new ApiError(response.status)
  if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Expected API JSON response')
  return response.json()
}

function adminSession(value: unknown): AdminSession {
  if (!value || typeof value !== 'object' || !('username' in value) || typeof value.username !== 'string' || !value.username.trim()) throw new Error('Invalid Admin session response')
  return { username: value.username }
}

async function csrfHeaders() {
  const value = await requestJson('/api/auth/csrf')
  if (!value || typeof value !== 'object' || !('token' in value) || typeof value.token !== 'string' || !value.token || !('headerName' in value) || value.headerName !== 'X-CSRF-TOKEN') throw new Error('Invalid CSRF response')
  return { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': value.token }
}

export class BackendUnavailableError extends Error {
  constructor() {
    super('Backend hiện chưa cung cấp API cho chức năng này.')
    this.name = 'BackendUnavailableError'
  }
}

// Session auth and Events use the canonical Admin API; other management pages
// keep unavailable states until their own adapters/actions are integrated.
export const capabilities = Object.freeze({
  monitoring: false,
  authentication: true,
  events: true,
  allow: false,
  block: false,
  revoke: false,
  settings: false,
})

export interface ProtectionApi {
  getSnapshot(signal: AbortSignal): Promise<Snapshot>
  changeDevicePolicy(id: string, action: DeviceAction): Promise<void>
  login(username: string, password: string): Promise<void>
  getCurrentAdmin(signal: AbortSignal): Promise<AdminSession>
  logout(): Promise<void>
  getEventPage(query: EventQuery, signal: AbortSignal): Promise<EventPage>
}

export const protectionApi: ProtectionApi = {
  async getSnapshot(signal) {
    signal.throwIfAborted()
    throw new BackendUnavailableError()
  },
  async changeDevicePolicy() {
    throw new BackendUnavailableError()
  },
  async login(username, password) {
    const headers = await csrfHeaders()
    adminSession(await requestJson('/api/auth/login', { method: 'POST', headers, body: JSON.stringify({ username, password }) }))
    await csrfHeaders()
  },
  async getCurrentAdmin(signal) {
    return adminSession(await requestJson('/api/auth/me', { signal }))
  },
  async logout() {
    const headers = await csrfHeaders()
    await requestJson('/api/auth/logout', { method: 'POST', headers, body: '{}' })
  },
  async getEventPage(query, signal) {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(query)) if (value !== undefined) params.set(key, String(value))
    return mapEventPage(await requestJson(`/api/admin/events?${params}`, { signal }))
  },
}
