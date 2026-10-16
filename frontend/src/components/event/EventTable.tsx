import { Badge } from '../Common'
import { DataTable } from '../DataTable'
import type { ServerPagination } from '../DataTable'
import type { UsbEvent } from '../../types/event'
import { eventLabels, formatDate } from '../../utils/format'
import './events.css'

export function EventTable({ events, filtered = false, loading = false, unavailable = false, failed = false, pagination }: { events: UsbEvent[]; filtered?: boolean; loading?: boolean; unavailable?: boolean; failed?: boolean; pagination?: ServerPagination }) {
  return <div className="event-history-table"><DataTable rows={events} pagination={pagination} loading={loading} unavailable={unavailable} failed={failed} filtered={filtered} label="Lịch sử sự kiện USB" columns={[
    { key: 'timestamp', label: 'Timestamp', render: event => <time dateTime={event.timestamp}>{formatDate(event.timestamp)}</time> },
    { key: 'endpoint', label: 'Endpoint', render: event => event.endpointId ? <a title={event.endpointId} href={`#/endpoints/${encodeURIComponent(event.endpointId)}`}>{event.endpointName ?? event.endpointId}</a> : event.endpointName ?? '—' },
    { key: 'user', label: 'Linux User', render: event => event.username ?? '—' },
    { key: 'usb', label: 'USB', render: event => <span title={event.device ? [event.device.vendorId, event.device.productId, event.device.serial, event.device.hash].filter(Boolean).join(' · ') : undefined}>{event.deviceName ?? '—'}{pagination && event.usbDeviceId && <small className="device-event-id">ID: {event.usbDeviceId}</small>}</span> },
    { key: 'event', label: 'Event', render: event => eventLabels[event.type] },
    { key: 'decision', label: 'Decision', render: event => {
      const decision = event.decision ?? (event.type === 'allowed' || event.type === 'blocked' ? event.type : undefined)
      return decision ? <Badge tone={decision === 'allowed' ? 'green' : decision === 'blocked' ? 'red' : 'amber'}>{decision === 'allowed' ? 'ALLOW' : decision === 'blocked' ? 'BLOCK' : 'UNKNOWN'}</Badge> : '—'
    } },
  ]} /></div>
}
