import type { DeviceStatus, EventType } from '../services/models'

export const deviceLabels: Record<DeviceStatus, string> = {
  allowed: 'Được phép', blocked: 'Bị chặn', detected: 'Đã phát hiện', disconnected: 'Đã tháo',
}
export const eventLabels: Record<EventType, string> = {
  connected: 'Cắm USB', disconnected: 'Tháo USB', blocked: 'Chặn USB', allowed: 'Cho phép USB', service: 'Service',
}
export function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' }).format(date)
}
export function count(value?: number | null) {
  return value == null ? '—' : new Intl.NumberFormat('vi-VN').format(value)
}
export function matches(query: string, ...values: (string | undefined)[]) {
  const normalize = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase()
  return normalize(values.filter(Boolean).join(' ')).includes(normalize(query.trim()))
}
export function newestFirst<T>(items: T[], date: (item: T) => string | undefined): T[] {
  const time = (item: T) => Date.parse(date(item) ?? '') || 0
  return [...items].sort((a, b) => time(b) - time(a))
}
export function paginate<T>(items: T[], requestedPage: number, pageSize = 10) {
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize))
  const page = Math.max(1, Math.min(requestedPage, totalPages))
  return { page, totalPages, rows: items.slice((page - 1) * pageSize, page * pageSize) }
}
