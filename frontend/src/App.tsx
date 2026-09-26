import { AdminLayout } from './layouts/AdminLayout'
import { ToastProvider } from './components/Toast'
import { useToast } from './hooks/useToast'
import { EmptyState, PageHeading, Panel } from './components/Common'
import { Icon } from './components/Icon'
import { useResource } from './hooks/useResource'
import { useRoute } from './hooks/useRoute'
import { protectionApi } from './services/api'
import { formatDate } from './utils/format'
import { Dashboard } from './pages/Dashboard'
import { Devices } from './pages/Devices'
import { Events } from './pages/Events'
import { Endpoints, EndpointDetail } from './pages/Endpoints'
import { Settings } from './pages/Settings'
import { Login } from './pages/Login'
import './App.css'
function Console({ route }: { route: string }) {
  const { state, refresh, updatedAt } = useResource(protectionApi.getSnapshot)
  const notify = useToast()
  const handleRefresh = async () => {
    const result = await refresh()
    if (result === 'ready') notify('Đã cập nhật dữ liệu mới nhất.', 'success')
    if (result === 'unavailable') notify('Chưa thể làm mới dữ liệu: backend chưa cung cấp API.')
    if (result === 'error') notify('Tải dữ liệu thất bại. Vui lòng thử lại.', 'error')
  }
  const props = { state, refresh: () => { void handleRefresh() }, refreshButton: <div className="refresh-actions">{updatedAt && <span>Cập nhật: {formatDate(updatedAt)}</span>}<button className="button" onClick={() => void handleRefresh()} disabled={state.status === 'loading'}><Icon name="refresh" size={16} className={state.status === 'loading' ? 'spin' : ''} />{state.status === 'loading' ? 'Đang tải…' : 'Refresh dữ liệu'}</button></div> }
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
      try { endpointId = match ? decodeURIComponent(match[1]) : undefined } catch { /* Invalid URL: show not-found page. */ }
      page = endpointId ? <EndpointDetail key={endpointId} {...props} endpointId={endpointId} /> : <><PageHeading eyebrow="404" title="Không tìm thấy trang" description="Đường dẫn này không tồn tại trong USB Protection." /><Panel title="Kiểm tra lại đường dẫn"><EmptyState icon="search" title="Trang không khả dụng" description="Sử dụng thanh điều hướng hoặc quay lại Dashboard." /><div className="not-found-action"><a className="button primary" href="#/dashboard">Về Dashboard <Icon name="arrow" size={16} /></a></div></Panel></>
    }
  }
  return <AdminLayout route={route}>{page}</AdminLayout>
}
function Router() { const route = useRoute(); return route === '/login' ? <Login /> : <Console route={route} /> }
export default function App() { return <ToastProvider><Router /></ToastProvider> }
