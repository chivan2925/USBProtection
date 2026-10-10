import { Icon } from '../Icon'
import type { EventFilterValues } from '../../types/event'
export function EventFilters({ value, onChange, onReset }: { value: EventFilterValues; onChange: (value: EventFilterValues) => void; onReset: () => void }) {
  const update = (key: keyof EventFilterValues, next: string) => onChange({ ...value, [key]: next })
  return <div className="filters event-filters">
    <label>Endpoint<input aria-label="Endpoint" value={value.endpoint} onChange={event => update('endpoint', event.target.value)} /></label>
    <label>Linux Username<input aria-label="Linux Username" value={value.username} onChange={event => update('username', event.target.value)} /></label>
    <label>Device<input aria-label="Device" value={value.device} onChange={event => update('device', event.target.value)} /></label>
    <label>Decision<select aria-label="Decision" value={value.decision} onChange={event => update('decision', event.target.value)}><option value="">Tất cả</option><option value="allowed">ALLOW</option><option value="blocked">BLOCK</option><option value="none">Không áp dụng</option></select></label>
    <label>Date<input type="date" aria-label="Date" value={value.date} onChange={event => update('date', event.target.value)} /></label>
    <button type="button" className="button small" aria-label="Đặt lại tất cả bộ lọc" title="Đặt lại tất cả bộ lọc" onClick={onReset}><Icon name="refresh" size={18} /></button>
  </div>
}
