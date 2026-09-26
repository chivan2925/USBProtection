import { Icon } from './Icon'
export function Filters({ query, onQuery, value, onValue, options, placeholder, filterLabel = 'Lọc theo trạng thái' }: {
  query: string; onQuery: (value: string) => void; value: string; onValue: (value: string) => void
  options: { value: string; label: string }[]; placeholder: string; filterLabel?: string
}) {
  return <div className="filters"><label className="search-input"><Icon name="search" size={18} /><input type="search" aria-label={placeholder} placeholder={placeholder} value={query} onChange={event => onQuery(event.target.value)} /></label><select aria-label={filterLabel} value={value} onChange={event => onValue(event.target.value)}>{options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select>{(query || value !== 'all') && <button className="text-button" onClick={() => { onQuery(''); onValue('all') }}>Đặt lại bộ lọc</button>}</div>
}
