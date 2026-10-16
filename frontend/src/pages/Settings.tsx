import { Badge, PageHeading, Panel } from '../components/Common'
import { useAuth } from '../hooks/useAuth'

export function Settings() {
  const auth = useAuth()
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
    <Panel title="Cấu hình dịch vụ">
      <div className="settings-explanation">
        <p>API cài đặt dịch vụ chưa có trong phạm vi này. Trạng thái USBGuard và trạng thái bảo vệ hiển thị “Chưa xác định” khi server chưa cung cấp dữ liệu xác minh.</p>
        <p>Allow/Revoke nằm trong trang chi tiết endpoint. Server lưu whitelist trước; Agent cần áp dụng và ACK phiên bản mới.</p>
      </div>
    </Panel>
  </>
}
