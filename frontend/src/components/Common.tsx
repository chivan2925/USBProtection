import type { ReactNode } from 'react'
import type { ResourceState } from '../hooks/useResource'
import { Icon } from './Icon'
import type { IconName } from './Icon'

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: string }) {
  return <span className={`badge ${tone}`}><span className="status-dot" />{children}</span>
}
export function PageHeading({ eyebrow, title, description, children }: { eyebrow: string; title: string; description: string; children?: ReactNode }) {
  return <div className="page-heading"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{description}</p></div><div className="heading-actions">{children}</div></div>
}
export function EmptyState({ title = 'Chưa có dữ liệu', description = 'Dữ liệu sẽ xuất hiện tại đây khi có hoạt động mới.', icon = 'usb' }: { title?: string; description?: string; icon?: IconName }) {
  return <div className="empty-state"><div className="empty-icon"><Icon name={icon} size={28} /></div><h3>{title}</h3><p>{description}</p></div>
}
export function ResourceNotice<T>({ state, retry }: { state: ResourceState<T>; retry: () => void }) {
  if (state.status === 'ready' || state.status === 'loading') return null
  const failed = state.status === 'error'
  return <div className={`notice ${failed ? 'error-notice' : ''}`} role={failed ? 'alert' : 'status'}><Icon name="info" /><div><strong>{failed ? 'Không thể tải dữ liệu' : 'Đang chờ kết nối backend'}</strong><p>{failed ? state.error : 'Backend chưa có API quản lý USB. Trạng thái bảo vệ và số liệu chưa được xác định; thao tác quản lý hiện chưa khả dụng.'}</p></div>{failed ? <button className="button small" onClick={retry}>Thử lại</button> : <a href="#/settings">Chi tiết <Icon name="arrow" size={15} /></a>}</div>
}
export function LoadingState() {
  return <div className="loading-state" role="status"><Icon name="refresh" className="spin" /><span>Đang tải dữ liệu…</span><div className="skeleton" /><div className="skeleton" /><div className="skeleton" /></div>
}
export function Panel({ title, subtitle, action, children, className = '' }: { title: string; subtitle?: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return <section className={`panel ${className}`}><div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>{children}</section>
}
