import { useCallback, useEffect, useRef, useState } from 'react'
import { Badge, PageHeading, Panel, ResourceNotice } from '../components/Common'
import { DataTable } from '../components/DataTable'
import { Filters } from '../components/Filters'
import { Icon } from '../components/Icon'
import { useResource } from '../hooks/useResource'
import { protectionApi } from '../services/api'
import { deviceLabels, formatDate, matches, newestFirst } from '../utils/format'
import type { PageProps } from './types'

type Mode = 'devices' | 'whitelist'
const pageCopy = {
  devices: { title: 'Quản lý thiết bị USB', table: 'Danh sách thiết bị' },
  whitelist: { title: 'Whitelist & Policy', table: 'Whitelist theo endpoint' },
}

export function Devices({ state, refresh, refreshButton, mode, demo = false }: PageProps & { mode: Mode }) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const [page, setPage] = useState(0)
  const copy = pageCopy[mode]
  const entries = state.data?.whitelist ?? []
  const endpoints = state.data?.endpoints ?? []
  const options = mode === 'devices'
    ? [{ value: 'all', label: 'Tất cả trạng thái' }, ...Object.entries(deviceLabels).map(([value, label]) => ({ value, label: value === 'detected' ? 'Chưa xác định' : label }))]
    : [{ value: 'all', label: 'Tất cả endpoints' }, ...endpoints.map(endpoint => ({ value: endpoint.id, label: endpoint.name }))]

  // Do not derive inventory from a truncated audit preview. Each filter/page is
  // sent to the backend and only the returned page is rendered.
  const loadDevices = useCallback((signal: AbortSignal) => {
    if (demo || mode === 'whitelist') return Promise.resolve({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
    return protectionApi.getDevicePage({
      page, size: 10, q: query.trim() || undefined,
      status: filter === 'all' ? undefined : filter as 'allowed' | 'blocked' | 'detected' | 'disconnected',
    }, signal)
  }, [demo, mode, page, filter, query])
  const deviceRead = useResource(loadDevices)
  const loadWhitelist = useCallback((signal: AbortSignal) => {
    if (demo || mode !== 'whitelist') return Promise.resolve({ content: [], page: 0, size: 10,
      totalElements: 0, totalPages: 0 })
    return protectionApi.getWhitelistPage({ page, size: 10, q: query.trim() || undefined,
      endpointId: filter === 'all' ? undefined : filter }, signal)
  }, [demo, mode, page, query, filter])
  const whitelistRead = useResource(loadWhitelist)
  const { refresh: refreshDevices } = deviceRead
  const { refresh: refreshWhitelist } = whitelistRead
  // useResource already loads the initial page and re-loads on filter/page changes.
  // The shared toolbar refresh returns a new parent snapshot: only that transition
  // should cause an additional device-page read, not every loader change.
  const lastReadySnapshot = useRef(state.status === 'ready' ? state.data : undefined)
  useEffect(() => {
    if (state.status !== 'ready') return
    const previous = lastReadySnapshot.current
    lastReadySnapshot.current = state.data
    if (!demo && previous && previous !== state.data) {
      if (mode === 'whitelist') void refreshWhitelist()
      else void refreshDevices()
    }
  }, [demo, mode, state.data, state.status, refreshDevices, refreshWhitelist])

  const demoDeviceRows = newestFirst((state.data?.devices ?? []).filter(device =>
    (filter === 'all' || device.status === filter) &&
    matches(query, device.name, device.vendorId, device.productId, device.serialNumber, device.endpointId, device.endpointName)),
    device => device.detectedAt)
  const devicePage = deviceRead.state.status === 'ready' ? deviceRead.state.data : undefined
  const deviceRows = demo ? demoDeviceRows : devicePage?.content ?? []
  const whitelistPage = whitelistRead.state.status === 'ready' ? whitelistRead.state.data : undefined
  const whitelistRows = demo ? entries.filter(entry =>
    (filter === 'all' || entry.endpointId === filter) &&
    matches(query, entry.name, entry.endpointId, entry.fingerprintValue, entry.vendorId, entry.productId, entry.serial))
    : whitelistPage?.content ?? []
  const selectedRead = mode === 'whitelist' ? whitelistRead.state : deviceRead.state
  const tableState = demo
    ? { loading: state.status === 'loading', unavailable: state.status === 'unavailable', failed: state.status === 'error', filtered: !!query || filter !== 'all' }
    : { loading: state.status === 'loading' || selectedRead.status === 'loading',
      unavailable: state.status === 'unavailable' || selectedRead.status === 'unavailable',
      failed: state.status === 'error' || selectedRead.status === 'error', filtered: !!query || filter !== 'all' }

  function endpointLink(endpointId?: string) {
    return endpointId ? <a href={`#/endpoints/${encodeURIComponent(endpointId)}`}>Quản lý trên endpoint</a> : 'Chưa rõ endpoint'
  }
  function changeQuery(value: string) { setPage(0); setQuery(value) }
  function changeFilter(value: string) { setPage(0); setFilter(value) }

  return <>
    <PageHeading title={copy.title}>{refreshButton}</PageHeading>
    <ResourceNotice state={state} retry={refresh} />
    {!demo && selectedRead.status === 'error' &&
      <p role="alert" className="field-error">Không tải được trang {mode === 'whitelist' ? 'whitelist' : 'thiết bị'}: {selectedRead.error}</p>}
    <Panel title={copy.table}>
      <Filters query={query} onQuery={changeQuery} value={filter} onValue={changeFilter} options={options} placeholder="Tìm USB, fingerprint hoặc endpoint…" filterLabel={mode === 'devices' ? 'Lọc theo trạng thái' : 'Lọc theo endpoint'} />
      
      {mode === 'whitelist'
        ? <DataTable key={demo ? `${query}:${filter}` : 'server-whitelist'} {...tableState} rows={whitelistRows} label={copy.table}
          pagination={!demo && whitelistPage ? { page: whitelistPage.page, size: whitelistPage.size,
            totalElements: whitelistPage.totalElements, totalPages: whitelistPage.totalPages, onPageChange: setPage } : undefined}
          columns={[
          { key: 'name', label: 'Thiết bị', render: entry => entry.name ?? 'USB' },
          { key: 'endpoint', label: 'Endpoint', render: entry => <span className="mono">{entry.endpointId}</span> },
          { key: 'fingerprint', label: 'Fingerprint', render: entry => <span className="fingerprint-cell">{entry.fingerprintType ? `${entry.fingerprintType}: ${entry.fingerprintValue}` : entry.hash ?? `${entry.vendorId}:${entry.productId}`}</span> },
          { key: 'time', label: 'Thêm lúc', render: entry => formatDate(entry.addedAt) },
          { key: 'actions', label: 'Thao tác', render: entry => endpointLink(entry.endpointId) },
        ]} />
        : <DataTable key={demo ? `${query}:${filter}` : 'server-devices'} {...tableState} rows={deviceRows} label={copy.table}
          pagination={!demo && devicePage ? { page: devicePage.page, size: devicePage.size, totalPages: devicePage.totalPages, totalElements: devicePage.totalElements, onPageChange: setPage } : undefined}
          columns={[
          { key: 'name', label: 'Thiết bị', render: device => <div className="table-device"><span className="table-icon"><Icon name="usb" size={18} /></span><div><strong>{device.name}</strong><small>{device.endpointName ?? device.endpointId ?? 'Chưa rõ endpoint'}</small></div></div> },
          { key: 'ids', label: 'Vendor / Product ID', render: device => <span className="mono">{device.vendorId ?? '—'} / {device.productId ?? '—'}</span> },
          { key: 'serial', label: 'Serial Number', render: device => <span className="mono">{device.serialNumber ?? '—'}</span> },
          { key: 'status', label: 'Quyết định gần nhất', render: device => <Badge tone={device.status === 'allowed' ? 'green' : device.status === 'blocked' ? 'red' : 'neutral'}>{deviceLabels[device.status]}</Badge> },
          { key: 'time', label: 'Thời gian event', render: device => formatDate(device.detectedAt) },
          { key: 'actions', label: 'Thao tác', render: device => endpointLink(device.endpointId) },
        ]} />}
    </Panel>
    
  </>
}
