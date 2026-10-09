import { Badge } from './Common'
import { DataTable } from './DataTable'
import type { UsbEvent } from '../services/models'
import { eventLabels, formatDate } from '../utils/format'

export function EventTable({ rows, filtered = false }: { rows: UsbEvent[]; filtered?: boolean }) {
  return <DataTable rows={rows} filtered={filtered} label="Lịch sử sự kiện USB" columns={[
    { key: 'timestamp', label: 'Timestamp', render: event => <time dateTime={event.timestamp}>{formatDate(event.timestamp)}</time> },
    { key: 'endpoint', label: 'Endpoint', render: event => event.endpointName ?? event.endpointId ?? '—' },
    { key: 'user', label: 'Linux User', render: event => event.username ?? '—' },
    { key: 'usb', label: 'USB', render: event => event.deviceName ?? '—' },
    { key: 'event', label: 'Event', render: event => eventLabels[event.type] },
    { key: 'decision', label: 'Decision', render: event => {
      const decision = event.decision ?? (event.type === 'allowed' || event.type === 'blocked' ? event.type : undefined)
      return decision ? <Badge tone={decision === 'allowed' ? 'green' : 'red'}>{decision === 'allowed' ? 'ALLOW' : 'BLOCK'}</Badge> : '—'
    } },
  ]} />
}
