import { useState } from 'react'
import { protectionApi } from '../services/api'
import { Badge, PageHeading, Panel } from '../components/Common'
import { useAuth } from '../hooks/useAuth'

export function Settings() {
  const auth = useAuth()
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  return <>
    <PageHeading eyebrow="SYSTEM CONFIGURATION" title="Cài đặt hệ thống" description="Thông tin phiên Admin và phạm vi quản lý USB." />
    <Panel title="Thông tin kết nối" action={<Badge>{auth?.user ? 'Phiên Admin' : 'Demo chỉ đọc'}</Badge>}>
      <dl className="settings-details">
        <div><dt>Tài khoản</dt><dd>{auth?.user?.username ?? 'Chưa đăng nhập'}</dd></div>
        <div><dt>Nguồn dữ liệu</dt><dd>{auth?.user ? 'Admin API · Refresh để cập nhật' : 'Demo · Dữ liệu minh họa'}</dd></div>
        <div><dt>Whitelist</dt><dd>Quản lý theo từng endpoint</dd></div>
        <div><dt>Trạng thái Agent</dt><dd>Xem Policy version / Last Applied Version trên endpoint</dd></div>
      </dl>
    </Panel>
    {auth?.user && <Panel title="Đổi mật khẩu Admin" subtitle="Đổi xong, mọi phiên Admin sẽ bị đăng xuất, kể cả phiên hiện tại.">
      <form className="policy-form" onSubmit={async event => {
        event.preventDefault(); setError(''); setBusy(true)
        try {
          await protectionApi.changeAdminPassword(oldPassword, newPassword)
          setOldPassword(''); setNewPassword('')
          // The Server invalidates the session; the next request establishes a fresh login.
          window.dispatchEvent(new Event('usbshield:session-expired'))
        } catch (failure) {
          setError(failure instanceof Error ? failure.message : 'Không đổi được mật khẩu.')
        } finally { setBusy(false) }
      }}>
        <label htmlFor="admin-old-password">Mật khẩu hiện tại</label>
        <input id="admin-old-password" type="password" autoComplete="current-password" required value={oldPassword} onChange={e => setOldPassword(e.target.value)} />
        <label htmlFor="admin-new-password">Mật khẩu mới (ít nhất 12 ký tự, tối đa 72 byte UTF-8)</label>
        <input id="admin-new-password" type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={newPassword} onChange={e => setNewPassword(e.target.value)} />
        {error && <p role="alert" className="field-error">{error}</p>}
        <button type="submit" className="button primary" disabled={busy}>{busy ? 'Đang đổi…' : 'Đổi mật khẩu và đăng xuất'}</button>
      </form>
    </Panel>}
    <Panel title="Cấu hình dịch vụ">
      <div className="settings-explanation">
        <p>API cấu hình USBGuard chưa có trong phạm vi này. Sức khỏe USBGuard trên trang Endpoint chỉ là bằng chứng từ Agent tại lần kiểm tra gần nhất; dữ liệu stale/offline hiển thị Unknown.</p>
        <p>Allow/Revoke nằm trong trang chi tiết endpoint. Server lưu whitelist trước; Agent cần áp dụng và ACK phiên bản mới.</p>
      </div>
    </Panel>
  </>
}
