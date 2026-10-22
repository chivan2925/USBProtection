import { useCallback, useEffect } from 'react'
import { AdminLayout } from './layouts/AdminLayout'
import { ToastProvider } from './components/Toast'
import { EmptyState, LoadingState, PageHeading, Panel } from './components/Common'
import { Icon } from './components/Icon'
import { useToast } from './hooks/useToast'
import { useResource } from './hooks/useResource'
import { useRoute } from './hooks/useRoute'
import { AuthProvider } from './hooks/AuthProvider'
import { useAuth } from './hooks/useAuth'
import { protectionApi } from './services/api'
import { getDemoSnapshot } from './services/demo'
import { formatDate } from './utils/format'
import { Dashboard } from './pages/Dashboard'
import { Devices } from './pages/Devices'
import { Events, LiveEvents } from './pages/Events'
import { Endpoints, EndpointDetail } from './pages/Endpoints'
import { Settings } from './pages/Settings'
import { Login } from './pages/Login'
import './App.css'

function Console({ route, demo }: { route: string; demo: boolean }) {
  const loader = useCallback((signal: AbortSignal) => {
    if (demo) return getDemoSnapshot(signal)
    const match = route.match(/^\/endpoints\/([^/]+)$/)
    let endpointId: string | undefined
    try { if (match) endpointId = decodeURIComponent(match[1]) } catch { /* Invalid URL shows 404. */ }
    return protectionApi.getSnapshot(signal, endpointId)
  }, [demo, route])
  const { state, refresh, updatedAt } = useResource(loader)
  const notify = useToast()

  async function handleRefresh() {
    const result = await refresh()
    if (result === 'ready') notify(demo ? 'Đã tải lại dữ liệu mock.' : 'Đã cập nhật dữ liệu mới nhất.', 'success')
    if (result === 'error') notify('Tải dữ liệu thất bại. Vui lòng thử lại.', 'error')
  }

  const props = {
    state, demo, refresh: () => { void handleRefresh() },
    refreshButton: <div className="refresh-actions">
      {updatedAt && <span>Cập nhật: {formatDate(updatedAt)}</span>}
      <button className="button" onClick={() => void handleRefresh()} disabled={state.status === 'loading'}>
        <Icon name="refresh" size={16} className={state.status === 'loading' ? 'spin' : ''} />
        {state.status === 'loading' ? 'Đang tải…' : 'Refresh dữ liệu'}
      </button>
    </div>,
  }
  let page
  switch (route) {
    case '/dashboard': page = <Dashboard {...props} />; break
    case '/devices': page = <Devices key="devices" {...props} mode="devices" />; break
    case '/whitelist': page = <Devices key="whitelist" {...props} mode="whitelist" />; break
    case '/blocked': page = <Devices key="blocked" {...props} mode="blocked" />; break
    case '/events': page = <Events {...props} />; break
    case '/endpoints': page = <Endpoints {...props} />; break
    case '/settings': page = <Settings />; break
    default: {
      const match = route.match(/^\/endpoints\/([^/]+)$/)
      let endpointId: string | undefined
      try { if (match) endpointId = decodeURIComponent(match[1]) } catch { /* Show 404. */ }
      page = endpointId
        ? <EndpointDetail key={endpointId} {...props} endpointId={endpointId} />
        : <><PageHeading eyebrow="404" title="Không tìm thấy trang" description="Đường dẫn này không tồn tại trong USB Protection." />
          <Panel title="Kiểm tra lại đường dẫn"><EmptyState icon="search" title="Trang không khả dụng" description="Sử dụng thanh điều hướng hoặc quay lại Dashboard." />
            <a className="button primary" href="#/dashboard">Về Dashboard</a>
          </Panel></>
    }
  }
  return <AdminLayout route={route} demo={demo}>
    <div className="notice" role="status"><Icon name="info" />
      <div><strong>{demo ? 'Chế độ demo · Dữ liệu mock' : 'Admin Web · Dữ liệu từ API'}</strong>
        <p>{demo ? 'Dữ liệu minh họa, chỉ đọc. Không thay đổi quyền USB trên máy thật.' : 'Allow/Revoke áp dụng cho endpoint được chọn. Theo dõi Last Applied Version để biết phiên bản Agent đã áp dụng.'}</p>
      </div>
      <a href={demo ? `${window.location.pathname}#/dashboard` : '?demo=1#/dashboard'}>{demo ? 'Thoát demo' : 'Mở demo'}</a>
    </div>
    {page}
  </AdminLayout>
}

function Router({ demo }: { demo: boolean }) {
  const route = useRoute()
  const auth = useAuth()
  useEffect(() => {
    if (demo || auth?.loading || auth?.error) return
    if (!auth?.user && route !== '/login') window.location.hash = '/login'
    if (auth?.user && route === '/login') window.location.hash = '/dashboard'
  }, [demo, auth?.loading, auth?.error, auth?.user, route])

  if (!demo && auth?.loading) return <main className="session-page"><LoadingState /></main>
  if (!demo && auth?.error) return <main className="session-page">
    <Panel title="Không kiểm tra được phiên Admin">
      <p role="alert" className="field-error">{auth.error}</p>
      <button className="button" onClick={auth.retry}>Thử lại kết nối</button>
      <a className="button" href="?demo=1#/dashboard">Xem demo chỉ đọc</a>
    </Panel>
  </main>
  if (route === '/login' || (!demo && !auth?.user)) return <Login demo={demo} />
  if (route === '/events' && !demo) return <AdminLayout route={route}><LiveEvents /></AdminLayout>
  // Mỗi trang bắt đầu bằng loading, không hiển thị snapshot của trang trước.
  return <Console key={route} route={route} demo={demo} />
}

export default function App() {
  const demo = new URLSearchParams(window.location.search).get('demo') === '1'
  return <ToastProvider><AuthProvider demo={demo}><Router demo={demo} /></AuthProvider></ToastProvider>
}
