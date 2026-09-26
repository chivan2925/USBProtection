import { Badge, PageHeading, Panel } from '../components/Common'
import { Icon } from '../components/Icon'
import type { IconName } from '../components/Icon'
const integrations: { icon: IconName; title: string; description: string }[] = [
  { icon: 'shield', title: 'USB Protection & Service', description: 'Trạng thái bảo vệ, bật/tắt dịch vụ và tự động chặn USB lạ.' },
  { icon: 'usb', title: 'Thiết bị USB', description: 'Danh sách thiết bị, định danh VID/PID/serial và thời điểm phát hiện.' },
  { icon: 'check', title: 'Whitelist & Policy', description: 'Cho phép, chặn và thu hồi quyền sử dụng USB theo endpoint.' },
  { icon: 'monitor', title: 'Endpoints', description: 'Máy Ubuntu, trạng thái online/offline, người dùng và policy version.' },
  { icon: 'history', title: 'Nhật ký sự kiện', description: 'Sự kiện cắm/rút USB, thay đổi quyền và hoạt động service.' },
  { icon: 'key', title: 'Đăng nhập quản trị', description: 'Xác thực username/password, phiên đăng nhập và phân quyền.' },
]
export function Settings() {
  return <><PageHeading eyebrow="SYSTEM CONFIGURATION" title="Cài đặt hệ thống" description="Thông tin tích hợp và các cấu hình bảo vệ được hệ thống hỗ trợ." /><div className="notice"><Icon name="info" /><div><strong>Chưa có cấu hình có thể cập nhật</strong><p>Backend chưa cung cấp API cài đặt. Bật/tắt bảo vệ, tự động chặn USB lạ và cấu hình service hiện chưa khả dụng.</p></div></div><Panel title="Khả năng tích hợp" subtitle="Các chức năng đang chờ backend triển khai" action={<Badge tone="amber">Chờ backend</Badge>}><div className="integration-list">{integrations.map(item => <div className="integration-row" key={item.title}><span className="table-icon"><Icon name={item.icon} /></span><div><h3>{item.title}</h3><p>{item.description}</p></div><Badge>Chưa có API</Badge></div>)}</div></Panel><div className="settings-grid"><Panel title="Thông tin ứng dụng"><dl className="settings-details"><div><dt>Ứng dụng</dt><dd>USB Protection</dd></div><div><dt>Giao diện</dt><dd>Web Admin Console</dd></div><div><dt>Nguồn dữ liệu</dt><dd>Chưa được kết nối</dd></div><div><dt>Xác thực</dt><dd>Chưa được triển khai</dd></div></dl></Panel><Panel title="Về trạng thái hiển thị"><div className="settings-explanation"><Icon name="shield" size={32} /><h3>Trạng thái chính xác từ hệ thống</h3><p>Dấu “—” biểu thị dữ liệu chưa xác định. Trạng thái “Chưa kết nối” không có nghĩa là máy tính đang được bảo vệ hoặc dịch vụ đã tắt.</p><p>Cấu hình và quyền quản lý chỉ khả dụng khi backend hỗ trợ.</p></div></Panel></div></>
}
