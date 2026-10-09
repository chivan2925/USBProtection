import type { ReactNode } from 'react'

// M5 owner — EventFilters component (dedicated event filter controls)
// Filter dimensions: Endpoint, Linux Username, Device, Decision, Date.

export interface EventFilterState {
  query: string
  endpointId: string
  decision: string
  dateFrom: string
  dateTo: string
}

interface Props {
  filters: EventFilterState
  onChange: (next: Partial<EventFilterState>) => void
  endpointOptions: { value: string; label: string }[]
}

export function EventFilters({ filters, onChange, endpointOptions }: Props): ReactNode {
  return (
    <div className="filters" data-testid="event-filters">
      {/* Search across device name, username, hostname */}
      <label className="search-input">
        <input
          id="event-search"
          type="search"
          aria-label="Tìm thiết bị, người dùng, endpoint"
          placeholder="Tìm thiết bị, người dùng, endpoint…"
          value={filters.query}
          onChange={e => onChange({ query: e.target.value })}
        />
      </label>

      {/* Filter by Endpoint */}
      <select
        id="event-filter-endpoint"
        aria-label="Lọc theo endpoint"
        value={filters.endpointId}
        onChange={e => onChange({ endpointId: e.target.value })}
      >
        <option value="">Tất cả endpoints</option>
        {endpointOptions.map(opt => (
          <option key={opt.value} value={opt.value}>{opt.label}</option>
        ))}
      </select>

      {/* Filter by Decision */}
      <select
        id="event-filter-decision"
        aria-label="Lọc theo decision"
        value={filters.decision}
        onChange={e => onChange({ decision: e.target.value })}
      >
        <option value="">Tất cả decisions</option>
        <option value="ALLOWED">ALLOWED</option>
        <option value="BLOCKED">BLOCKED</option>
        <option value="UNKNOWN">UNKNOWN</option>
      </select>

      {/* Date range */}
      <label htmlFor="event-date-from" className="sr-only">Từ ngày</label>
      <input
        id="event-date-from"
        type="date"
        aria-label="Từ ngày"
        value={filters.dateFrom}
        onChange={e => onChange({ dateFrom: e.target.value })}
      />

      <label htmlFor="event-date-to" className="sr-only">Đến ngày</label>
      <input
        id="event-date-to"
        type="date"
        aria-label="Đến ngày"
        value={filters.dateTo}
        onChange={e => onChange({ dateTo: e.target.value })}
      />

      {/* Reset */}
      {(filters.query || filters.endpointId || filters.decision || filters.dateFrom || filters.dateTo) && (
        <button
          className="text-button"
          onClick={() => onChange({ query: '', endpointId: '', decision: '', dateFrom: '', dateTo: '' })}
        >
          Đặt lại bộ lọc
        </button>
      )}
    </div>
  )
}
