import { useState } from 'react'
import { Badge, EmptyState, PageHeading, Panel, ResourceNotice } from '../components/Common'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { DataTable } from '../components/DataTable'
import { Filters } from '../components/Filters'
import { Icon } from '../components/Icon'
import { EndpointWhitelistPanel } from '../components/endpoint/EndpointWhitelistPanel'
import { EndpointStatusBadge } from '../components/endpoint/EndpointStatusBadge'
import { useAuth } from '../hooks/useAuth'
import { useToast } from '../hooks/useToast'
import { protectionApi } from '../services/api'
import { deviceLabels, eventLabels, formatDate, matches, newestFirst } from '../utils/format'
import type { PageProps } from './types'

const endpointLabels = { online: 'Online', offline: 'Offline', unknown: 'Chưa xác định' }

export function Endpoints({ state, refresh, refreshButton }: PageProps) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const rows = (state.data?.endpoints ?? []).filter(endpoint =>
    (filter === 'all' || endpoint.status === filter) &&
    matches(query, endpoint.id, endpoint.name, endpoint.hostname, endpoint.ipAddress, endpoint.currentUser))
  return <>
    <PageHeading eyebrow="ENDPOINT SECURITY" title="Quản lý endpoints" description="Theo dõi các máy Ubuntu và trạng thái kết nối với hệ thống.">{refreshButton}</PageHeading>
    <ResourceNotice state={state} retry={refresh} />
    <Panel title="Danh sách máy tính" subtitle="Trạng thái kết nối, người dùng và phiên bản chính sách" action={<Badge>Managed endpoints</Badge>}>
      <Filters query={query} onQuery={setQuery} value={filter} onValue={setFilter} options={[{ value: 'all', label: 'Tất cả trạng thái' }, ...Object.entries(endpointLabels).map(([value, label]) => ({ value, label }))]} placeholder="Tìm endpoint ID, hostname, IP hoặc người dùng…" />
      <DataTable key={`${query}:${filter}`} rows={rows} label="Danh sách endpoints" loading={state.status === 'loading'} unavailable={state.status === 'unavailable'} failed={state.status === 'error'} filtered={!!query || filter !== 'all'} columns={[
        { key: 'name', label: 'Máy tính', render: endpoint => <a className="table-device" href={`#/endpoints/${encodeURIComponent(endpoint.id)}`}><span className="table-icon"><Icon name="monitor" size={18} /></span><div><strong>{endpoint.name}</strong><small>{endpoint.hostname ?? '—'}</small></div></a> },
        { key: 'id', label: 'Endpoint ID', render: endpoint => <span className="mono">{endpoint.id}</span> },
        { key: 'os', label: 'Hệ điều hành', render: endpoint => endpoint.operatingSystem ?? '—' },
        { key: 'status', label: 'Trạng thái', render: endpoint => <EndpointStatusBadge status={endpoint.status} /> },
        { key: 'user', label: 'Người dùng', render: endpoint => endpoint.currentUser ?? '—' },
        { key: 'policy', label: 'Policy version', render: endpoint => <span className="mono">{endpoint.policyVersion ?? '—'}</span> },
        { key: 'appliedPolicy', label: 'Last Applied Version', render: endpoint => <span className="mono">{endpoint.appliedPolicyVersion ?? '—'}</span> },
        { key: 'seen', label: 'Kết nối gần nhất', render: endpoint => formatDate(endpoint.lastSeenAt) },
        { key: 'action', label: 'Chi tiết', render: endpoint => <a href={`#/endpoints/${encodeURIComponent(endpoint.id)}`}>Xem chi tiết →</a> },
      ]} />
    </Panel>
  </>
}

type PendingAction = { action: 'allow'; usbDeviceId: number; hash: string } | { action: 'revoke'; entryId: string }

export function EndpointDetail({ state, refresh, refreshButton, endpointId, demo = false }: PageProps & { endpointId: string }) {
  const [showForm, setShowForm] = useState(false)
  const [usbDeviceId, setUsbDeviceId] = useState('')
  const [pending, setPending] = useState<PendingAction>()
  const auth = useAuth()
  const notify = useToast()
  const endpoint = state.data?.endpoints.find(item => item.id === endpointId)
  const devices = newestFirst((state.data?.devices ?? []).filter(device => device.endpointId === endpointId), device => device.detectedAt)
  const allowableDevices = devices.filter(device => device.usbDeviceId && device.hash)
  const events = newestFirst((state.data?.events ?? []).filter(event => event.endpointId === endpointId), event => event.timestamp)
  const entries = state.data?.whitelist ?? []
  const disabled = demo || !auth?.user || state.status !== 'ready'
  const tableState = { loading: state.status === 'loading', unavailable: state.status === 'unavailable', failed: state.status === 'error' }
  const latestUser = events.find(event => event.username)?.username
  const versionsKnown = endpoint?.policyVersion !== undefined && endpoint?.appliedPolicyVersion !== undefined
  const synced = versionsKnown && endpoint.policyVersion === endpoint.appliedPolicyVersion

  async function confirmPolicy() {
    if (!pending || disabled) throw new Error('Phiên Admin không hợp lệ hoặc dữ liệu đang tải.')
    if (pending.action === 'allow') {
      await protectionApi.allowOnEndpoint(endpointId, pending)
    } else {
      await protectionApi.revokeOnEndpoint(endpointId, pending.entryId)
    }
    setShowForm(false)
    setUsbDeviceId('')
    notify('Server đã lưu thay đổi whitelist. Đang tải lại phiên bản; Agent cần áp dụng chính sách.', 'success')
    refresh()
  }

  return <>
    <a className="back-link" href="#/endpoints">← Danh sách endpoints</a>
    <PageHeading eyebrow="ENDPOINT DETAIL" title={endpoint?.name ?? 'Chi tiết endpoint'} description="Thông tin máy tính, người dùng và chính sách USB theo endpoint.">{refreshButton}</PageHeading>
    <ResourceNotice state={state} retry={refresh} />
    {state.status === 'ready' && !endpoint
      ? <Panel title="Không tìm thấy endpoint"><EmptyState icon="monitor" title="Endpoint không tồn tại" description="Hãy quay lại danh sách endpoints." /></Panel>
      : <>
        <Panel title="Thông tin máy tính" action={<EndpointStatusBadge status={endpoint?.status ?? 'unknown'} />}>
          <dl className="details-grid">{[
            ['Hostname', endpoint?.hostname], ['IP', endpoint?.ipAddress], ['Hệ điều hành', endpoint?.operatingSystem],
            ['Người dùng hiện tại (backend)', endpoint?.currentUser], ['Người dùng gần nhất (event)', latestUser],
            ['Policy version', endpoint?.policyVersion], ['Last Applied Version', endpoint?.appliedPolicyVersion],
            ['Kết nối gần nhất', formatDate(endpoint?.lastSeenAt)], ['Endpoint ID', endpoint?.id],
          ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? '—'}</dd></div>)}</dl>
          {endpoint && <div className="panel-bottom"><Badge tone={synced ? 'green' : 'amber'}>{!versionsKnown ? 'Chưa có dữ liệu ACK' : synced ? 'Agent đã ACK phiên bản này' : 'Chờ Agent áp dụng chính sách'}</Badge></div>}
        </Panel>
        {endpoint && <EndpointWhitelistPanel endpointId={endpointId} entries={entries} disabled={disabled} disabledReason={demo ? 'Demo chỉ đọc; không thay đổi whitelist.' : 'Cần phiên Admin hợp lệ và dữ liệu đã tải.'} onAllow={() => setShowForm(true)} onRevoke={entryId => setPending({ action: 'revoke', entryId })} />}
        {endpoint && showForm && !disabled && <Panel title="Cho phép USB trên endpoint này">
          <form className="policy-form" onSubmit={event => {
            event.preventDefault()
            const device = allowableDevices.find(item => String(item.usbDeviceId) === usbDeviceId)
            if (device?.usbDeviceId && device.hash) setPending({ action: 'allow', usbDeviceId: device.usbDeviceId, hash: device.hash })
          }}>
            <p>Endpoint: <strong className="mono">{endpointId}</strong></p>
            <label htmlFor="usb-device-id">USB đã ghi nhận trên endpoint</label>
            <select id="usb-device-id" required value={usbDeviceId} onChange={event => setUsbDeviceId(event.target.value)}>
              <option value="">Chọn USB</option>
              {allowableDevices.map(device => <option key={device.id} value={device.usbDeviceId}>{device.name} · HASH: {device.hash}</option>)}
            </select>
            <p>{allowableDevices.length ? 'Server cấp quyền theo HASH của USB đã chọn, chỉ trên endpoint này.' : 'Chưa có USB với HASH và ID từ backend. Cần Agent gửi sự kiện trước khi cấp quyền.'}</p>
            <div className="row-actions"><button className="button primary" type="submit">Kiểm tra và xác nhận</button><button className="button" type="button" onClick={() => setShowForm(false)}>Hủy</button></div>
          </form>
        </Panel>}
        <Panel title="USB & Policy trên endpoint" subtitle="Quyết định trong sự kiện gần nhất; thay đổi whitelist cần Agent áp dụng">
          <DataTable {...tableState} rows={devices} label="USB trên endpoint" columns={[
            { key: 'name', label: 'Thiết bị', render: device => device.name },
            { key: 'serial', label: 'Serial Number', render: device => device.serialNumber ?? '—' },
            { key: 'status', label: 'Quyết định gần nhất', render: device => <Badge tone={device.status === 'allowed' ? 'green' : device.status === 'blocked' ? 'red' : 'neutral'}>{deviceLabels[device.status]}</Badge> },
            { key: 'time', label: 'Thời gian event', render: device => formatDate(device.detectedAt) },
          ]} />
        </Panel>
        <Panel title="Sự kiện trên endpoint" subtitle="Lịch sử cắm, tháo và kiểm soát USB">
          <DataTable {...tableState} rows={events} label="Sự kiện trên endpoint" columns={[
            { key: 'time', label: 'Thời gian', render: event => formatDate(event.timestamp) },
            { key: 'type', label: 'Sự kiện', render: event => eventLabels[event.type] },
            { key: 'message', label: 'USB / Nội dung', render: event => event.message || event.deviceName || '—' },
            { key: 'user', label: 'Người dùng', render: event => event.username ?? '—' },
          ]} />
          {events.some(event => event.device?.hash) && <details className="policy-form"><summary>Hash USB trong sự kiện</summary>
            {events.filter(event => event.device?.hash).map(event => <p key={event.id}>{event.deviceName ?? 'USB'}: <code>{event.device?.hash}</code></p>)}
          </details>}
        </Panel>
      </>}
    {pending && <ConfirmDialog
      title={pending.action === 'allow' ? 'Allow on this endpoint' : 'Revoke on this endpoint'}
      description={pending.action === 'allow'
        ? `Cho phép USB ID ${pending.usbDeviceId}, HASH = ${pending.hash} trên endpoint ${endpointId}?`
        : `Thu hồi entry ${pending.entryId} trên endpoint ${endpointId}? Agent cần áp dụng phiên bản mới để USB bị chặn lại.`}
      confirmLabel="Xác nhận thay đổi" onClose={() => setPending(undefined)} onConfirm={confirmPolicy}
    />}
  </>
}
