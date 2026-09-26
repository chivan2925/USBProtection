export type IconName = 'shield' | 'dashboard' | 'usb' | 'check' | 'block' | 'history' | 'settings' | 'monitor' | 'arrow' | 'refresh' | 'search' | 'info' | 'close' | 'menu' | 'logout' | 'clock' | 'user' | 'key'
const paths: Record<IconName, string> = {
  shield: 'M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6l-8-3Z M8 12l3 3 5-6',
  dashboard: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  usb: 'M12 21V3m-3 3 3-3 3 3 M12 15l-6-4V8 M12 12l6-4V5 M4 5h4v3H4z M16 2h4v3h-4z M10 19h4v2h-4z',
  check: 'M20 6 9 17l-5-5', block: 'M5 5l14 14 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  history: 'M3 11a9 9 0 1 1 2 7 M3 4v7h7 M12 7v5l3 2',
  settings: 'm9 3-1 3-3 1-2 5 2 5 3 1 1 3h6l1-3 3-1 2-5-2-5-3-1-1-3H9Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  monitor: 'M3 4h18v13H3z M12 17v4 M8 21h8', arrow: 'M5 12h14m-5-5 5 5-5 5',
  refresh: 'M20 7a9 9 0 0 0-15-2L3 8 M3 3v5h5 M4 17a9 9 0 0 0 15 2l2-3 M16 16h5v5',
  search: 'M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  info: 'M12 11v6 M12 7h.01 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  close: 'm6 6 12 12 M6 18 18 6', menu: 'M3 6h18 M3 12h18 M3 18h18',
  logout: 'M9 4H4v16h5 M10 12h11m-4-4 4 4-4 4', clock: 'M12 7v5l3 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-3a8 6 0 0 1 16 0v3',
  key: 'M14 9a5 5 0 1 1-10 0 5 5 0 0 1 10 0 M13 12l8 8m-4-4-2 2m4 0-2 2',
}
export function Icon({ name, size = 20, className = '' }: { name: IconName; size?: number; className?: string }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true"><path d={paths[name]} /></svg>
}
