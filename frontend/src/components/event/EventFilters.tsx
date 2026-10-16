import { Icon } from '../Icon'
import type { EventFilterValues } from '../../types/event'
export function EventFilters({ value, onChange, onReset, live = false }: { value: EventFilterValues; onChange: (value: EventFilterValues) => void; onReset: () => void; live?: boolean }) {
  const update = (key: keyof EventFilterValues, next: string) => onChange({ ...value, [key]: next })
  return <div className="filters event-filters">
    <label>{live ? 'Endpoint ID (chính xác)' : 'Endpoint'}<input aria-label="Endpoint" value={value.endpoint} onChange={event => update('endpoint', event.target.value)} /></label>
    <label>{live ? 'Linux Username (chính xác)' : 'Linux Username'}<input aria-label="Linux Username" value={value.username} onChange={event => update('username', event.target.value)} /></label>
    <label>{live ? 'Device ID' : 'Device'}<input aria-label="Device" inputMode={live ? 'numeric' : undefined} value={value.device} onChange={event => update('device', event.target.value)} /></label>
    <label>Event Type<select aria-label="Event Type" value={value.eventType} onChange={event => update('eventType', event.target.value)}><option value="">Tất cả</option><option value="connected">Cắm USB</option><option value="disconnected">Tháo USB</option>{!live && <><option value="allowed">Cho phép USB</option><option value="blocked">Chặn USB</option><option value="service">Service</option></>}</select></label>
    <label>Decision<select aria-label="Decision" value={value.decision} onChange={event => update('decision', event.target.value)}><option value="">Tất cả</option><option value="allowed">ALLOW</option><option value="blocked">BLOCK</option>{!live && <><option value="unknown">UNKNOWN</option><option value="none">Không áp dụng</option></>}</select></label>
    <label>Từ ngày<input type="date" aria-label="From Date" value={value.dateFrom} max={value.dateTo || undefined} onChange={event => update('dateFrom', event.target.value)} /></label>
    <label>Đến ngày<input type="date" aria-label="To Date" value={value.dateTo} min={value.dateFrom || undefined} onChange={event => update('dateTo', event.target.value)} /></label>
    <button type="button" className="button small" aria-label="Đặt lại tất cả bộ lọc" title="Đặt lại tất cả bộ lọc" onClick={onReset}><Icon name="refresh" size={18} /></button>
  </div>
}
