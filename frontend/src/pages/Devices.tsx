import { useState } from 'react'
import { Badge, PageHeading, Panel, ResourceNotice } from '../components/Common'
import { DataTable } from '../components/DataTable'
import { Filters } from '../components/Filters'
import { Icon } from '../components/Icon'
import { deviceLabels, formatDate, matches, newestFirst } from '../utils/format'
import type { PageProps } from './types'

type Mode = 'devices' | 'whitelist' | 'blocked'
const pageCopy = {
  devices: { title: 'Quản lý thiết bị USB', description: 'USB đã được ghi nhận trong lịch sử sự kiện.', table: 'Danh sách thiết bị' },
  whitelist: { title: 'Whitelist & Policy', description: 'Mỗi quyền sử dụng USB thuộc một endpoint cụ thể.', table: 'Whitelist theo endpoint' },
  blocked: { title: 'Thiết bị bị chặn', description: 'USB có quyết định BLOCKED trong sự kiện gần nhất.', table: 'Danh sách chặn' },
}

export function Devices({ state, refresh, refreshButton, mode }: PageProps & { mode: Mode }) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const copy = pageCopy[mode]
  const devices = state.data?.devices ?? []
  const entries = state.data?.whitelist ?? []
  const endpoints = state.data?.endpoints ?? []
  const options = mode === 'devices'
    ? [{ value: 'all', label: 'Tất cả trạng thái' }, ...Object.entries(deviceLabels).map(([value, label]) => ({ value, label }))]
    : [{ value: 'all', label: 'Tất cả endpoints' }, ...endpoints.map(endpoint => ({ value: endpoint.id, label: endpoint.name }))]

  const deviceRows = newestFirst(devices.filter(device =>
    (mode !== 'blocked' || device.status === 'blocked') &&
    (filter === 'all' || (mode === 'devices' ? device.status : device.endpointId) === filter) &&
    matches(query, device.name, device.vendorId, device.productId, device.serialNumber, device.endpointId, device.endpointName)),
    device => device.detectedAt)
  const whitelistRows = entries.filter(entry =>
    (filter === 'all' || entry.endpointId === filter) &&
    matches(query, entry.name, entry.endpointId, entry.fingerprintValue, entry.vendorId, entry.productId, entry.serial))
  const tableState = { loading: state.status === 'loading', unavailable: state.status === 'unavailable', failed: state.status === 'error', filtered: !!query || filter !== 'all' }

  function endpointLink(endpointId?: string) {
    return endpointId ? <a href={`#/endpoints/${encodeURIComponent(endpointId)}`}>Quản lý trên endpoint</a> : 'Chưa rõ endpoint'
  }

  return <>
    <PageHeading eyebrow="DEVICE MANAGEMENT" title={copy.title} description={copy.description}>{refreshButton}</PageHeading>
    <ResourceNotice state={state} retry={refresh} />
    <Panel title={copy.table} subtitle={mode === 'whitelist' ? 'Danh sách quyền từ API whitelist; Agent áp dụng theo policy version' : 'Trạng thái quan sát từ event; không suy ra quyền whitelist'} action={<Badge>{mode === 'whitelist' ? 'Per-endpoint rules' : 'USB history'}</Badge>}>
      <Filters query={query} onQuery={setQuery} value={filter} onValue={setFilter} options={options} placeholder="Tìm USB, fingerprint hoặc endpoint…" filterLabel={mode === 'devices' ? 'Lọc theo trạng thái' : 'Lọc theo endpoint'} />
      {mode === 'whitelist'
        ? <DataTable key={`${query}:${filter}`} {...tableState} rows={whitelistRows} label={copy.table} columns={[
          { key: 'name', label: 'Thiết bị', render: entry => entry.name ?? 'USB' },
          { key: 'endpoint', label: 'Endpoint', render: entry => <span className="mono">{entry.endpointId}</span> },
          { key: 'fingerprint', label: 'Fingerprint', render: entry => <span className="fingerprint-cell">{entry.fingerprintType ? `${entry.fingerprintType}: ${entry.fingerprintValue}` : entry.hash ?? `${entry.vendorId}:${entry.productId}`}</span> },
          { key: 'time', label: 'Thêm lúc', render: entry => formatDate(entry.addedAt) },
          { key: 'actions', label: 'Thao tác', render: entry => endpointLink(entry.endpointId) },
        ]} />
        : <DataTable key={`${query}:${filter}`} {...tableState} rows={deviceRows} label={copy.table} columns={[
          { key: 'name', label: 'Thiết bị', render: device => <div className="table-device"><span className="table-icon"><Icon name="usb" size={18} /></span><div><strong>{device.name}</strong><small>{device.endpointName ?? device.endpointId ?? 'Chưa rõ endpoint'}</small></div></div> },
          { key: 'ids', label: 'Vendor / Product ID', render: device => <span className="mono">{device.vendorId ?? '—'} / {device.productId ?? '—'}</span> },
          { key: 'serial', label: 'Serial Number', render: device => <span className="mono">{device.serialNumber ?? '—'}</span> },
          { key: 'status', label: 'Quyết định gần nhất', render: device => <Badge tone={device.status === 'allowed' ? 'green' : device.status === 'blocked' ? 'red' : 'neutral'}>{deviceLabels[device.status]}</Badge> },
          { key: 'time', label: 'Thời gian event', render: device => formatDate(device.detectedAt) },
          { key: 'actions', label: 'Thao tác', render: device => endpointLink(device.endpointId) },
        ]} />}
    </Panel>
    <div className="helper-note"><Icon name="info" size={17} /><p>Mở chi tiết endpoint để Allow/Revoke USB trên máy đó. Theo dõi Last Applied Version sau thay đổi.</p></div>
  </>
}
