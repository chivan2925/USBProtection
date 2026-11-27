import { useEffect, useState } from 'react'
import { Icon } from '../components/Icon'
import { useToast } from '../hooks/useToast'
import { useAuth } from '../hooks/useAuth'
import { ApiError } from '../services/http'

export function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const auth = useAuth()
  const notify = useToast()
  useEffect(() => { document.title = 'Đăng nhập · USBShield' }, [])

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!auth) return
    setBusy(true)
    setError('')
    try {
      await auth.login(username.trim(), password)
      notify('Đăng nhập thành công.', 'success')
      window.location.hash = '/dashboard'
    } catch (problem) {
      const message = problem instanceof ApiError && problem.status === 401
        ? 'Tên đăng nhập hoặc mật khẩu không đúng.'
        : problem instanceof Error ? problem.message : 'Không thể đăng nhập. Vui lòng thử lại.'
      setError(message)
    } finally {
      setPassword('')
      setBusy(false)
    }
  }

  return <main className="login-page">
    <section className="login-story">
      <a className="brand" href="#/dashboard"><span className="brand-mark"><Icon name="shield" size={25} /></span><span>USB<span className="brand-light">Shield</span></span></a>
      <div className="login-story-content">
        <div className="login-shield"><Icon name="shield" size={86} /></div>
        <h1>An toàn bắt đầu<br />từ mỗi kết nối.</h1>
        <div className="login-features"><span><Icon name="monitor" />Quản lý endpoints</span><span><Icon name="check" />Kiểm soát truy cập USB</span><span><Icon name="history" />Theo dõi hoạt động</span></div>
      </div>
    </section>
    <section className="login-form-panel"><div className="login-form-content">
      <h2>Chào mừng trở lại</h2>
      {auth?.sessionExpired && <p className="notice" role="status">Phiên Admin đã hết hạn. Vui lòng đăng nhập lại.</p>}
      <form onSubmit={submit}>
        <label htmlFor="username">Tên đăng nhập</label>
        <input id="username" name="username" autoComplete="username" placeholder="Nhập tên đăng nhập" value={username} onChange={event => setUsername(event.target.value)} required disabled={busy} />
        <label htmlFor="password">Mật khẩu</label>
        <div className="password-field">
          <input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="Nhập mật khẩu" value={password} onChange={event => setPassword(event.target.value)} required disabled={busy} />
          <button type="button" className="text-button" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={showPassword} disabled={busy} onClick={() => setShowPassword(!showPassword)}>{showPassword ? 'Ẩn' : 'Hiện'}</button>
        </div>
        {error && <p role="alert" className="field-error">{error}</p>}
        <button className="button primary login-submit" type="submit" disabled={busy}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}<Icon name="arrow" size={17} /></button>
      </form>
    </div></section>
  </main>
}
