import { useState } from 'react'
import { Badge, PageHeading, Panel, ResourceNotice } from '../components/Common'
import { EventTable } from '../components/event/EventTable'
import { EventFilters } from '../components/event/EventFilters'
import { emptyEventFilters } from '../types/event'
import { getEventDateRange, matchesEventFilters } from '../utils/eventFilters'
import { formatDate as formatDateForEvents, newestFirst } from '../utils/format'
import type { PageProps } from './types'
import { useCallback, useEffect } from 'react'
import { useResource } from '../hooks/useResource'
import { protectionApi } from '../services/api'
import { ApiError } from '../services/http'
import { getEventQuery } from '../utils/eventFilters'

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

export function LiveEvents({ onUnauthorized }: { onUnauthorized?: () => void }) {
  const [filters, setFilters] = useState(emptyEventFilters)
  const [page, setPage] = useState(0)
  const size = 20
  const loader = useCallback(async (signal: AbortSignal) => {
    let query
    try { query = getEventQuery(filters, page, size) }
    catch (error) { throw new ApiError(400, error instanceof Error ? error.message : 'Bộ lọc không hợp lệ.') }
    const result = await protectionApi.getEventPage(query, signal)
    // A concurrent deletion can shrink the history while viewing its last page.
    if (query.page > 0 && query.page >= result.totalPages) return protectionApi.getEventPage({ ...query, page: Math.max(0, result.totalPages - 1) }, signal)
    return result
  }, [filters, page])
  const { state, refresh, updatedAt } = useResource(loader)
  useEffect(() => { if (state.status === 'error' && state.statusCode === 401) onUnauthorized?.() }, [state, onUnauthorized])
  const updateFilters = (next: typeof filters) => { setFilters(next); setPage(0) }
  const data = state.status === 'ready' ? state.data : undefined
  return <>
    <PageHeading eyebrow="AUDIT & ACTIVITY" title="Lịch sử sự kiện" description="Sự kiện đã được máy chủ lưu, sắp xếp mới nhất trước.">
      <div className="refresh-actions">{updatedAt && state.status === 'ready' && <span>Cập nhật: {formatDateForEvents(updatedAt)}</span>}<button className="button" disabled={state.status === 'loading'} onClick={() => void refresh()}>Refresh dữ liệu</button></div>
    </PageHeading>
    <ResourceNotice state={state} retry={() => void refresh()} />
    <Panel title="Event Logs" subtitle="Lọc chính xác trên toàn bộ lịch sử · Ngày theo múi giờ trình duyệt" action={<Badge>{state.status === 'ready' ? 'Dữ liệu máy chủ' : 'Chưa tải được dữ liệu'}</Badge>}>
      <EventFilters live value={filters} onChange={updateFilters} onReset={() => updateFilters(emptyEventFilters)} />
      <EventTable events={data?.content ?? []} filtered={Object.values(filters).some(Boolean)} loading={state.status === 'loading'} failed={state.status === 'error'} pagination={{ page: data?.page ?? page, size, totalElements: data?.totalElements ?? 0, totalPages: data?.totalPages ?? 0, onPageChange: setPage }} />
    </Panel>
  </>
}
