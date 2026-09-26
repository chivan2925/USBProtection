import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Badge } from '../components/Common'
import { Icon } from '../components/Icon'
import type { IconName } from '../components/Icon'

const navigation: { route: string; label: string; icon: IconName; group: string }[] = [
  { route: '/dashboard', label: 'Dashboard', icon: 'dashboard', group: 'TỔNG QUAN' },
  { route: '/endpoints', label: 'Endpoints', icon: 'monitor', group: 'TỔNG QUAN' },
  { route: '/devices', label: 'Devices', icon: 'usb', group: 'QUẢN LÝ USB' },
  { route: '/whitelist', label: 'Whitelist', icon: 'check', group: 'QUẢN LÝ USB' },
  { route: '/blocked', label: 'Blocked Devices', icon: 'block', group: 'QUẢN LÝ USB' },
  { route: '/events', label: 'Event Logs', icon: 'history', group: 'HỆ THỐNG' },
  { route: '/settings', label: 'Settings', icon: 'settings', group: 'HỆ THỐNG' },
]
export function AdminLayout({ route, children }: { route: string; children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const mainRef = useRef<HTMLElement>(null)
  const active = navigation.find(item => route === item.route || route.startsWith(`${item.route}/`))
  useEffect(() => {
    document.title = `${active?.label ?? 'Không tìm thấy'} · USB Protection`
    mainRef.current?.focus({ preventScroll: true })
    window.scrollTo(0, 0)
  }, [route, active?.label])
  useEffect(() => {
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false) }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [])
  return <div className="app-shell"><a className="skip-link" href="#main-content" onClick={event => { event.preventDefault(); mainRef.current?.focus() }}>Đến nội dung chính</a>{open && <button className="sidebar-overlay" aria-label="Đóng menu" onClick={() => setOpen(false)} />}
    <aside className={`sidebar ${open ? 'is-open' : ''}`} id="sidebar"><a href="#/dashboard" className="brand" onClick={() => setOpen(false)}><span className="brand-mark"><Icon name="shield" size={25} /></span><span>USB<span className="brand-light">Protection</span><small>DEVICE SECURITY CONSOLE</small></span></a><div className="workspace-label"><span className="workspace-avatar">U</span><div>Không gian quản lý<small>USB Protection</small></div><span className="workspace-dot" /></div>
      <nav aria-label="Điều hướng chính">{navigation.map((item, index) => <div key={item.route}>{navigation[index - 1]?.group !== item.group && <p className="nav-group">{item.group}</p>}<a href={`#${item.route}`} className={`nav-link ${active?.route === item.route ? 'active' : ''}`} aria-current={active?.route === item.route ? 'page' : undefined} onClick={() => setOpen(false)}><Icon name={item.icon} /><span>{item.label}</span>{active?.route === item.route && <span className="nav-active-dot" />}</a></div>)}</nav>
      <div className="sidebar-bottom"><div className="sidebar-note"><Icon name="shield" /><strong>Bảo vệ từ mỗi kết nối</strong><p>Kiểm soát thiết bị. Giữ an toàn cho hệ thống.</p></div><a href="#/login" className="sidebar-user" onClick={() => setOpen(false)}><span className="user-avatar"><Icon name="user" size={18} /></span><span>Chưa đăng nhập<small>Đăng nhập quản trị</small></span><Icon name="logout" size={17} /></a></div>
    </aside><div className="main-shell"><header className="topbar"><div className="breadcrumbs"><button className="icon-button mobile-toggle" aria-label={open ? 'Đóng menu' : 'Mở menu'} aria-expanded={open} aria-controls="sidebar" onClick={() => setOpen(!open)}><Icon name="menu" /></button><span className="breadcrumb-root">Workspace</span><span className="breadcrumb-slash">/</span><strong>{active?.label ?? 'Không tìm thấy'}</strong></div><div className="topbar-right"><Badge>Chưa kết nối</Badge><span className="topbar-divider" /><a href="#/login" className="profile-link" aria-label="Đăng nhập quản trị"><span className="user-avatar"><Icon name="user" size={18} /></span><span>Quản trị viên</span></a></div></header><main id="main-content" ref={mainRef} tabIndex={-1}>{children}</main><footer className="app-footer"><span>USB Protection <span className="footer-dot">·</span> Quản lý bảo mật thiết bị</span><span>Trạng thái hệ thống chưa được xác định</span></footer></div>
  </div>
}
