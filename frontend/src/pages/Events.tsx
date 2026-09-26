import { useState } from 'react'
import { Badge, PageHeading, Panel, ResourceNotice } from '../components/Common'
import { DataTable } from '../components/DataTable'
import { Filters } from '../components/Filters'
import { eventLabels, formatDate, matches, newestFirst } from '../utils/format'
import type { PageProps } from './types'
export function Events({ state, refresh, refreshButton }: PageProps) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const rows = newestFirst((state.data?.events ?? []).filter(event => (filter === 'all' || event.type === filter) && matches(query, event.message, event.deviceName, event.endpointName, event.username)), event => event.timestamp)
  return <><PageHeading eyebrow="AUDIT & ACTIVITY" title="Lịch sử sự kiện" description="Tra cứu hoạt động USB và các sự kiện dịch vụ trên toàn hệ thống.">{refreshButton}</PageHeading><ResourceNotice state={state} retry={refresh} /><Panel title="Event Logs" subtitle="Mới nhất trước · Thời gian theo múi giờ trình duyệt" action={<Badge>Nhật ký hệ thống</Badge>}><Filters query={query} onQuery={setQuery} value={filter} onValue={setFilter} options={[{ value: 'all', label: 'Tất cả loại sự kiện' }, ...Object.entries(eventLabels).map(([value, label]) => ({ value, label }))]} placeholder="Tìm thiết bị, endpoint, người dùng, nội dung…" filterLabel="Lọc theo loại sự kiện" /><DataTable key={`${query}:${filter}`} rows={rows} label="Lịch sử sự kiện USB" loading={state.status === 'loading'} unavailable={state.status === 'unavailable'} failed={state.status === 'error'} filtered={!!query || filter !== 'all'} columns={[
    { key: 'timestamp', label: 'Thời gian', render: event => <time dateTime={event.timestamp}>{formatDate(event.timestamp)}</time> },
    { key: 'type', label: 'Loại sự kiện', render: event => <Badge tone={event.type === 'blocked' ? 'red' : event.type === 'allowed' ? 'green' : 'neutral'}>{eventLabels[event.type]}</Badge> },
    { key: 'device', label: 'Thiết bị', render: event => event.deviceName ?? '—' },
    { key: 'endpoint', label: 'Endpoint', render: event => event.endpointId ? <a href={`#/endpoints/${encodeURIComponent(event.endpointId)}`}>{event.endpointName ?? event.endpointId}</a> : event.endpointName ?? '—' },
    { key: 'user', label: 'Người dùng', render: event => event.username ?? '—' },
    { key: 'message', label: 'Nội dung', render: event => event.message },
  ]} /></Panel></>
}
