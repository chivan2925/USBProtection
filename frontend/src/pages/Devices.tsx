import { useState } from 'react'
import { Badge, PageHeading, Panel, ResourceNotice } from '../components/Common'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { DataTable } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import { Filters } from '../components/Filters'
import { Icon } from '../components/Icon'
import { useToast } from '../hooks/useToast'
import { capabilities, protectionApi } from '../services/api'
import type { Device, DeviceAction } from '../services/models'
import { deviceLabels, formatDate, matches, newestFirst } from '../utils/format'
import type { PageProps } from './types'
type Mode = 'devices' | 'whitelist' | 'blocked'
const pageCopy = {
  devices: { title: 'Quản lý thiết bị USB', description: 'Theo dõi và quản lý quyền truy cập của các thiết bị USB.', table: 'Danh sách thiết bị', eyebrow: 'DEVICE MANAGEMENT' },
  whitelist: { title: 'Whitelist & Policy', description: 'Quản lý các thiết bị USB được phép sử dụng trong hệ thống.', table: 'Thiết bị được phép', eyebrow: 'ACCESS CONTROL' },
  blocked: { title: 'Thiết bị bị chặn', description: 'Theo dõi các thiết bị USB bị từ chối truy cập và lý do chặn.', table: 'Danh sách chặn', eyebrow: 'THREAT CONTROL' },
}
const actionLabels: Record<DeviceAction, string> = { allow: 'Cho phép USB', block: 'Chặn USB', revoke: 'Xóa khỏi whitelist' }
export function Devices({ state, refresh, refreshButton, mode }: PageProps & { mode: Mode }) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const [pending, setPending] = useState<{ device: Device; action: DeviceAction }>()
  const notify = useToast()
  const copy = pageCopy[mode]
  const candidates = (state.data?.devices ?? []).filter(device => mode === 'devices' || device.status === (mode === 'whitelist' ? 'allowed' : 'blocked'))
  const endpoints = [...new Map(candidates.filter(device => device.endpointId).map(device => [device.endpointId!, device.endpointName ?? device.endpointId!])).entries()]
  const options = mode === 'devices' ? [{ value: 'all', label: 'Tất cả trạng thái' }, ...Object.entries(deviceLabels).map(([value, label]) => ({ value, label }))] : [{ value: 'all', label: 'Tất cả endpoints' }, ...endpoints.map(([value, label]) => ({ value, label }))]
  const rows = newestFirst(candidates.filter(device => matches(query, device.name, device.vendorId, device.productId, device.serialNumber, device.endpointName, device.blockReason) && (filter === 'all' || (mode === 'devices' ? device.status : device.endpointId) === filter)), device => mode === 'blocked' ? device.blockedAt : device.detectedAt)
  const actionButton = (device: Device, action: DeviceAction) => <button key={action} className={`text-button ${action !== 'allow' ? 'danger-text' : ''}`} disabled={!capabilities[action]} title={!capabilities[action] ? 'Chờ API quản lý chính sách từ backend' : undefined} onClick={() => setPending({ device, action })}>{actionLabels[action]}</button>
  const columns: Column<Device>[] = [
    { key: 'name', label: 'Thiết bị', render: device => <div className="table-device"><span className="table-icon"><Icon name="usb" size={18} /></span><div><strong>{device.name}</strong><small>{device.endpointName ?? 'Chưa rõ endpoint'}</small></div></div> },
    { key: 'ids', label: 'Vendor / Product ID', render: device => <span className="mono">{device.vendorId ?? '—'} / {device.productId ?? '—'}</span> },
    { key: 'serial', label: 'Serial Number', render: device => <span className="mono">{device.serialNumber ?? '—'}</span> },
    { key: 'status', label: 'Trạng thái', render: device => <Badge tone={device.status === 'allowed' ? 'green' : device.status === 'blocked' ? 'red' : 'neutral'}>{deviceLabels[device.status]}</Badge> },
    { key: 'time', label: mode === 'blocked' ? 'Thời gian chặn' : 'Phát hiện lúc', render: device => formatDate(mode === 'blocked' ? device.blockedAt : device.detectedAt) },
    ...(mode === 'blocked' ? [{ key: 'reason', label: 'Lý do chặn', render: (device: Device) => device.blockReason ?? '—' }] : []),
    { key: 'actions', label: 'Thao tác', render: device => <div className="row-actions">{mode === 'whitelist' ? actionButton(device, 'revoke') : <>{device.status !== 'allowed' && actionButton(device, 'allow')}{device.status !== 'blocked' && actionButton(device, 'block')}</>}</div> },
  ]
  return <><PageHeading eyebrow={copy.eyebrow} title={copy.title} description={copy.description}>{refreshButton}</PageHeading><ResourceNotice state={state} retry={refresh} /><Panel title={copy.table} subtitle={state.status === 'ready' ? `${candidates.length} thiết bị trong danh sách` : 'Chưa có dữ liệu từ hệ thống'} action={<Badge tone={mode === 'whitelist' ? 'green' : mode === 'blocked' ? 'red' : 'neutral'}>{mode === 'whitelist' ? 'Allowed devices' : mode === 'blocked' ? 'Blocked devices' : 'USB inventory'}</Badge>}><Filters query={query} onQuery={setQuery} value={filter} onValue={setFilter} options={options} placeholder="Tìm tên, VID, PID, serial hoặc endpoint…" filterLabel={mode === 'devices' ? 'Lọc theo trạng thái' : 'Lọc theo endpoint'} /><DataTable key={`${mode}:${query}:${filter}`} rows={rows} columns={columns} label={copy.table} loading={state.status === 'loading'} unavailable={state.status === 'unavailable'} failed={state.status === 'error'} filtered={!!query || filter !== 'all'} /></Panel><div className="helper-note"><Icon name="info" size={17} /><p>{mode === 'whitelist' ? 'Thêm, cho phép và thu hồi quyền USB đang chờ API whitelist/policy. Việc xóa khỏi whitelist cần xác nhận trước khi thực hiện.' : 'Các thao tác cho phép hoặc chặn USB đang chờ API chính sách. Khi khả dụng, mọi thay đổi đều cần được xác nhận.'}</p></div>{pending && <ConfirmDialog title={actionLabels[pending.action]} description={`Bạn có chắc muốn ${actionLabels[pending.action].toLowerCase()} đối với “${pending.device.name}” (${pending.device.serialNumber ?? pending.device.id})? Thay đổi có thể ảnh hưởng đến quyền sử dụng thiết bị.`} confirmLabel="Xác nhận thay đổi" onClose={() => setPending(undefined)} onConfirm={async () => { try { await protectionApi.changeDevicePolicy(pending.device.id, pending.action); notify('Đã cập nhật chính sách thiết bị.', 'success'); refresh() } catch (error) { notify('Không thể cập nhật chính sách thiết bị.', 'error'); throw error } }} />}</>
}
