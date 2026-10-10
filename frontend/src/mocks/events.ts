import type { UsbEvent } from '../services/models'

export const mockEvents: UsbEvent[] = [
  { id: 'mock-1', timestamp: '2026-10-05T10:30:00+07:00', endpointName: 'LAB-PC-02', username: 'employee07', deviceName: 'SanDisk Ultra 32GB', type: 'connected', decision: 'allowed', message: 'USB trong whitelist được phép kết nối.' },
  { id: 'mock-2', timestamp: '2026-10-05T10:25:00+07:00', endpointName: 'LAB-PC-01', username: 'student01', deviceName: 'Kingston DataTraveler 64GB', type: 'connected', decision: 'blocked', message: 'USB ngoài whitelist bị chặn.' },
  { id: 'mock-3', timestamp: '2026-10-05T10:20:00+07:00', endpointName: 'linux-lab-01', username: 'huy', deviceName: 'Logitech USB Keyboard', type: 'allowed', decision: 'allowed', message: 'Bàn phím USB được cho phép.' },
  { id: 'mock-4', timestamp: '2026-10-05T10:15:00+07:00', endpointName: 'LAB-PC-02', username: 'employee07', deviceName: 'SanDisk Ultra 32GB', type: 'disconnected', message: 'USB đã được tháo khỏi máy.' },
  { id: 'mock-5', timestamp: '2026-10-05T10:10:00+07:00', endpointName: 'linux-lab-02', username: 'an', deviceName: 'Samsung Portable SSD T7', type: 'blocked', decision: 'blocked', message: 'Thiết bị lưu trữ bị chặn theo chính sách.' },
  { id: 'mock-6', timestamp: '2026-10-05T10:05:00+07:00', endpointName: 'linux-lab-01', username: 'root', type: 'service', message: 'Dịch vụ USBGuard đã khởi động.' },
]
