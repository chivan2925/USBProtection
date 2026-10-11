import { useState } from 'react'
import { Badge, EmptyState, PageHeading, Panel, ResourceNotice } from '../components/Common'
import { DataTable } from '../components/DataTable'
import { Filters } from '../components/Filters'
import { Icon } from '../components/Icon'
import { deviceLabels, eventLabels, formatDate, matches, newestFirst } from '../utils/format'
import type { PageProps } from './types'
const endpointLabels = { online: 'Online', offline: 'Offline', unknown: 'Chưa xác định' }
export function Endpoints({ state, refresh, refreshButton }: PageProps) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const rows = (state.data?.endpoints ?? []).filter(endpoint => (filter === 'all' || endpoint.status === filter) && matches(query, endpoint.name, endpoint.hostname, endpoint.currentUser))
  return <><PageHeading eyebrow="ENDPOINT SECURITY" title="Quản lý endpoints" description="Theo dõi các máy Ubuntu và trạng thái kết nối với hệ thống.">{refreshButton}</PageHeading><ResourceNotice state={state} retry={refresh} /><Panel title="Danh sách máy tính" subtitle="Trạng thái kết nối, người dùng và phiên bản chính sách" action={<Badge>Managed endpoints</Badge>}><Filters query={query} onQuery={setQuery} value={filter} onValue={setFilter} options={[{ value: 'all', label: 'Tất cả trạng thái' }, ...Object.entries(endpointLabels).map(([value, label]) => ({ value, label }))]} placeholder="Tìm tên máy, hostname hoặc người dùng…" /><DataTable key={`${query}:${filter}`} rows={rows} label="Danh sách endpoints" loading={state.status === 'loading'} unavailable={state.status === 'unavailable'} failed={state.status === 'error'} filtered={!!query || filter !== 'all'} columns={[
    { key: 'name', label: 'Máy tính', render: endpoint => <a className="table-device" href={`#/endpoints/${encodeURIComponent(endpoint.id)}`}><span className="table-icon"><Icon name="monitor" size={18} /></span><div><strong>{endpoint.name}</strong><small>{endpoint.hostname ?? '—'}</small></div></a> },
    { key: 'os', label: 'Hệ điều hành', render: endpoint => endpoint.operatingSystem ?? '—' },
    { key: 'status', label: 'Trạng thái', render: endpoint => <Badge tone={endpoint.status === 'online' ? 'green' : 'neutral'}>{endpointLabels[endpoint.status]}</Badge> },
    { key: 'user', label: 'Người dùng', render: endpoint => endpoint.currentUser ?? '—' },
    { key: 'policy', label: 'Policy version', render: endpoint => <span className="mono">{endpoint.policyVersion ?? '—'}</span> },
    { key: 'seen', label: 'Kết nối gần nhất', render: endpoint => formatDate(endpoint.lastSeenAt) },
    { key: 'action', label: 'Chi tiết', render: endpoint => <a href={`#/endpoints/${encodeURIComponent(endpoint.id)}`}>Xem chi tiết →</a> },
  ]} /></Panel></>
}
export function EndpointDetail({ state, refresh, refreshButton, endpointId }: PageProps & { endpointId: string }) {
  const endpoint = state.data?.endpoints.find(item => item.id === endpointId)
  const devices = newestFirst((state.data?.devices ?? []).filter(device => device.endpointId === endpointId), device => device.detectedAt)
  const events = newestFirst((state.data?.events ?? []).filter(event => event.endpointId === endpointId), event => event.timestamp)
  const tableState = { loading: state.status === 'loading', unavailable: state.status === 'unavailable', failed: state.status === 'error' }
  return <><a className="back-link" href="#/endpoints">← Danh sách endpoints</a><PageHeading eyebrow="ENDPOINT DETAIL" title={endpoint?.name ?? 'Chi tiết endpoint'} description="Thông tin máy tính, người dùng và chính sách USB theo endpoint.">{refreshButton}</PageHeading><ResourceNotice state={state} retry={refresh} />{state.status === 'ready' && !endpoint ? <Panel title="Không tìm thấy endpoint"><EmptyState icon="monitor" title="Endpoint không tồn tại" description="Máy tính này không có trong dữ liệu hiện tại. Hãy quay lại danh sách endpoints." /></Panel> : <><Panel title="Thông tin máy tính" action={<Badge tone={endpoint?.status === 'online' ? 'green' : 'neutral'}>{endpoint ? endpointLabels[endpoint.status] : 'Chưa xác định'}</Badge>}><dl className="details-grid">{[['Hostname', endpoint?.hostname], ['Hệ điều hành', endpoint?.operatingSystem], ['Người dùng hiện tại', endpoint?.currentUser], ['Policy version', endpoint?.policyVersion], ['Kết nối gần nhất', formatDate(endpoint?.lastSeenAt)], ['Endpoint ID', endpoint?.id]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? '—'}</dd></div>)}</dl></Panel><Panel title="USB & Policy trên endpoint" subtitle="Thiết bị gần đây và quyền sử dụng hiện tại" action={<a className="panel-link" href="#/whitelist">Whitelist <Icon name="arrow" size={15} /></a>}><DataTable {...tableState} rows={devices} label="USB trên endpoint" columns={[{ key: 'name', label: 'Thiết bị', render: device => device.name }, { key: 'serial', label: 'Serial Number', render: device => device.serialNumber ?? '—' }, { key: 'status', label: 'Chính sách', render: device => <Badge tone={device.status === 'allowed' ? 'green' : device.status === 'blocked' ? 'red' : 'neutral'}>{deviceLabels[device.status]}</Badge> }, { key: 'time', label: 'Phát hiện lúc', render: device => formatDate(device.detectedAt) }]} /></Panel><Panel title="Sự kiện trên endpoint" subtitle="Lịch sử cắm, tháo và kiểm soát USB"><DataTable {...tableState} rows={events} label="Sự kiện trên endpoint" columns={[{ key: 'time', label: 'Thời gian', render: event => formatDate(event.timestamp) }, { key: 'type', label: 'Sự kiện', render: event => eventLabels[event.type] }, { key: 'message', label: 'Nội dung', render: event => event.message }, { key: 'user', label: 'Người dùng', render: event => event.username ?? '—' }]} /></Panel></>}</>
}
