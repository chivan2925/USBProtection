import type { ReactNode } from 'react'
import type { UsbEvent } from '../../types/event'
import { formatDate } from '../../utils/format'

// M5 owner — EventTable component (dedicated, not using shared DataTable)
// Columns required: Timestamp, Endpoint, Linux User, USB, Event, Decision.

interface Props {
  events: UsbEvent[]
  loading?: boolean
}

const decisionTone: Record<string, string> = {
  ALLOWED: 'green',
  BLOCKED: 'red',
  UNKNOWN: 'neutral',
}

export function EventTable({ events, loading = false }: Props): ReactNode {
  if (loading) {
    return (
      <div className="loading-state" role="status">
        <span>Đang tải lịch sử sự kiện…</span>
      </div>
    )
  }

  if (events.length === 0) {
    return (
      <div className="empty-state">
        <p>Không có sự kiện nào phù hợp với bộ lọc hiện tại.</p>
      </div>
    )
  }

  return (
    <div className="table-scroll" data-testid="event-table">
      <table>
        <caption className="sr-only">Lịch sử sự kiện USB</caption>
        <thead>
          <tr>
            <th scope="col">Thời gian</th>
            <th scope="col">Endpoint</th>
            <th scope="col">Linux User</th>
            <th scope="col">USB</th>
            <th scope="col">Sự kiện</th>
            <th scope="col">Decision</th>
          </tr>
        </thead>
        <tbody>
          {events.map(event => (
            <tr key={event.id}>
              <td>
                <time dateTime={event.occurredAt}>{formatDate(event.occurredAt)}</time>
              </td>
              <td>
                {event.endpointId ? (
                  <a href={`#/endpoints/${encodeURIComponent(event.endpointId)}`}>
                    {event.hostname ?? event.endpointId}
                  </a>
                ) : (
                  event.hostname ?? '—'
                )}
              </td>
              <td>{event.linuxUsername ?? '—'}</td>
              <td>{event.device?.name ?? '—'}</td>
              <td>{event.eventType}</td>
              <td>
                <span
                  className={`badge ${decisionTone[event.decision] ?? 'neutral'}`}
                  data-testid={`decision-${event.id}`}
                >
                  <span className="status-dot" />
                  {event.decision}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
