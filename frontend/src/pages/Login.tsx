import { useEffect, useState } from 'react'
import { Icon } from '../components/Icon'
import { Badge } from '../components/Common'
import { useToast } from '../hooks/useToast'
import { ApiError, protectionApi } from '../services/api'

export function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const demo = typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('demo') === '1'
  const notify = useToast()
  useEffect(() => { document.title = 'Đăng nhập · USB Protection' }, [])
  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (demo || busy) return
    setBusy(true)
    setError('')
    try {
      await protectionApi.login(username, password)
      setPassword('')
      notify('Đăng nhập thành công.', 'success')
      window.location.hash = '/events'
    } catch (failure) {
      setPassword('')
      setError(failure instanceof ApiError && failure.status === 401 ? 'Tên đăng nhập hoặc mật khẩu không đúng.' : 'Không thể đăng nhập. Kiểm tra kết nối với máy chủ và thử lại.')
    } finally { setBusy(false) }
  }
  return <main className="login-page">
    <section className="login-story">
      <a className="brand" href="#/events"><span className="brand-mark"><Icon name="shield" size={25} /></span><span>USB<span className="brand-light">Protection</span><small>DEVICE SECURITY CONSOLE</small></span></a>
      <div className="login-story-content"><div className="login-shield"><Icon name="shield" size={86} /></div><div className="eyebrow">CONTROL EVERY CONNECTION</div><h1>An toàn bắt đầu<br />từ mỗi kết nối.</h1><p>Một không gian quản lý tập trung cho thiết bị USB, endpoints và chính sách bảo vệ hệ thống.</p><div className="login-features"><span><Icon name="monitor" />Quản lý endpoints</span><span><Icon name="check" />Kiểm soát truy cập USB</span><span><Icon name="history" />Theo dõi hoạt động</span></div></div>
      <small>USB Protection · Device security management</small>
    </section>
    <section className="login-form-panel"><div className="login-form-content">
      <Badge>ADMIN CONSOLE</Badge><h2>Chào mừng trở lại</h2><p>Đăng nhập để xem lịch sử sự kiện trên máy chủ.</p>
      {demo && <div className="notice"><Icon name="info" /><div><strong>Chế độ demo</strong><p>Thoát demo để đăng nhập bằng tài khoản Admin thật.</p><a href={`${window.location.pathname}#/login`}>Thoát demo và đăng nhập</a></div></div>}
      <form onSubmit={event => void submit(event)}>
        <label htmlFor="username">Tên đăng nhập</label><input id="username" name="username" autoComplete="username" placeholder="Nhập tên đăng nhập" value={username} onChange={event => setUsername(event.target.value)} required disabled={demo || busy} />
        <label htmlFor="password">Mật khẩu</label><div className="password-field"><input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="Nhập mật khẩu" value={password} onChange={event => setPassword(event.target.value)} required disabled={demo || busy} /><button type="button" className="text-button" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={showPassword} disabled={demo || busy} onClick={() => setShowPassword(!showPassword)}>{showPassword ? 'Ẩn' : 'Hiện'}</button></div>
        {error && <p role="alert" className="field-error">{error}</p>}
        <button className="button primary login-submit" type="submit" disabled={demo || busy}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}<Icon name="arrow" size={17} /></button>
      </form>
      <div className="login-divider"><span>Xem thử giao diện</span></div><a className="button login-preview" href="?demo=1#/dashboard">Xem demo Admin Web <Icon name="arrow" size={17} /></a><p className="login-footnote">Demo dùng dữ liệu mẫu và không cấp quyền quản trị.</p>
    </div></section>
  </main>
}
