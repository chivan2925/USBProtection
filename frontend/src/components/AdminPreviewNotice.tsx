import { Icon } from './Icon'

export function AdminPreviewNotice() {
  return <div className="notice" role="status"><Icon name="info" /><div>
    <strong>Dữ liệu mock · Demo Week 1</strong>
    <p>Backend chưa có API cho giao diện này. Số liệu, endpoint và whitelist bên dưới là dữ liệu minh họa. Allow/Revoke chưa khả dụng; trạng thái bảo vệ thực tế chưa được xác định.</p>
  </div></div>
}
