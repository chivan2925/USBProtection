import { useState } from 'react'
import { Badge, PageHeading, Panel } from '../components/Common'
import { EventTable } from '../components/event/EventTable'
import { EventFilters } from '../components/event/EventFilters'
import { emptyEventFilters } from '../types/event'
import { matchesEventFilters } from '../utils/eventFilters'
import { mockEvents } from '../mocks/events'
import { newestFirst } from '../utils/format'
import type { PageProps } from './types'

export function Events({ state, refreshButton }: PageProps) {
  const [eventFilters, setEventFilters] = useState(emptyEventFilters)
  const events = state.status === 'ready' && state.data ? state.data.events : mockEvents
  const useMockData = events === mockEvents
  const rows = newestFirst(events.filter(event => matchesEventFilters(event, eventFilters)), event => event.timestamp)

  return <>
    <PageHeading eyebrow="AUDIT & ACTIVITY" title="Lịch sử sự kiện" description="Tra cứu hoạt động USB và các sự kiện dịch vụ trên toàn hệ thống.">{refreshButton}</PageHeading>
    <Panel title="Event Logs" subtitle="Mới nhất trước · Thời gian theo múi giờ trình duyệt" action={<Badge>{useMockData ? 'Dữ liệu mock' : 'Nhật ký hệ thống'}</Badge>}>
      <EventFilters value={eventFilters} onChange={setEventFilters} onReset={() => setEventFilters(emptyEventFilters)} />
      <EventTable key={`${JSON.stringify(eventFilters)}`} events={rows} filtered={Object.values(eventFilters).some(Boolean)} />
    </Panel>
  </>
}
