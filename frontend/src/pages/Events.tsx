import { useState } from 'react'
import { Badge, PageHeading, Panel, ResourceNotice } from '../components/Common'
import { EventTable } from '../components/event/EventTable'
import { EventFilters } from '../components/event/EventFilters'
import { emptyEventFilters } from '../types/event'
import { getEventDateRange, matchesEventFilters } from '../utils/eventFilters'
import { newestFirst } from '../utils/format'
import type { PageProps } from './types'

export function Events({ state, refresh, refreshButton }: PageProps) {
  const [eventFilters, setEventFilters] = useState(emptyEventFilters)
  const events = state.status === 'ready' ? state.data.events : []
  const demo = typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('demo') === '1'
  const validDateRange = getEventDateRange(eventFilters).valid
  const rows = newestFirst(events.filter(event => matchesEventFilters(event, eventFilters)), event => event.timestamp)

  return <>
    <PageHeading eyebrow="AUDIT & ACTIVITY" title="Lịch sử sự kiện" description="Tra cứu hoạt động USB và các sự kiện dịch vụ trên toàn hệ thống.">{refreshButton}</PageHeading>
    <ResourceNotice state={state} retry={refresh} />
    <Panel title="Event Logs" subtitle="Mới nhất trước · Khoảng ngày bao gồm cả ngày đầu và cuối, theo múi giờ trình duyệt" action={<Badge>{demo ? 'Dữ liệu mock · Demo' : state.status === 'ready' ? 'Nhật ký hệ thống' : 'Chưa có dữ liệu'}</Badge>}>
      <EventFilters value={eventFilters} onChange={setEventFilters} onReset={() => setEventFilters(emptyEventFilters)} />
      {!validDateRange && <p className="field-error" role="alert">Khoảng ngày không hợp lệ. Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc.</p>}
      <EventTable key={JSON.stringify(eventFilters)} events={rows} filtered={Object.values(eventFilters).some(Boolean)} loading={state.status === 'loading'} unavailable={state.status === 'unavailable'} failed={state.status === 'error'} />
    </Panel>
  </>
}
