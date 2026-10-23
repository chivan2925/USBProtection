import { useState } from 'react'
import type { ReactNode } from 'react'
import { paginate } from '../utils/format'
import { EmptyState, LoadingState } from './Common'
export interface Column<T> { key: string; label: string; render: (row: T) => ReactNode }
export interface ServerPagination { page: number; size: number; totalElements: number; totalPages: number; onPageChange: (page: number) => void }
export function DataTable<T extends { id: string }>({ rows, columns, loading = false, unavailable = false, failed = false, filtered = false, label, pagination }: {
  rows: T[]; columns: Column<T>[]; loading?: boolean; unavailable?: boolean; failed?: boolean; filtered?: boolean; label: string; pagination?: ServerPagination
}) {
  const [requestedPage, setPage] = useState(1)
  const local = paginate(rows, requestedPage)
  const visible = pagination ? rows : local.rows
  const page = pagination ? pagination.page + 1 : local.page
  const totalPages = pagination ? Math.max(1, pagination.totalPages) : local.totalPages
  const total = pagination ? pagination.totalElements : rows.length
  const pageSize = pagination ? pagination.size : 10
  const disabled = loading || failed || unavailable
  const changePage = (next: number) => pagination ? pagination.onPageChange(next - 1) : setPage(next)
  return <div aria-busy={loading}>
    <div className="table-scroll"><table><caption className="sr-only">{label}</caption><thead><tr>{columns.map(column => <th key={column.key} scope="col">{column.label}</th>)}</tr></thead><tbody>{!loading && visible.map(row => <tr key={row.id}>{columns.map(column => <td key={column.key}>{column.render(row)}</td>)}</tr>)}</tbody></table></div>
    {loading ? <LoadingState /> : !visible.length && <EmptyState title={failed ? 'Dữ liệu tạm thời không khả dụng' : unavailable ? 'Chưa có nguồn dữ liệu' : filtered ? 'Không tìm thấy kết quả' : 'Chưa có dữ liệu'} description={failed ? 'Hãy thử tải lại bằng nút Refresh phía trên.' : unavailable ? 'Danh sách sẽ được cập nhật khi backend cung cấp API tương ứng.' : filtered ? 'Thử từ khóa khác hoặc đặt lại bộ lọc.' : 'Các bản ghi sẽ xuất hiện tại đây khi hệ thống nhận được dữ liệu.'} />}
    <div className="table-footer"><span>{disabled ? 'Chưa có dữ liệu để phân trang' : `${visible.length ? (page - 1) * pageSize + 1 : 0}–${visible.length ? (page - 1) * pageSize + visible.length : 0} trên ${total} bản ghi`}</span><div><button className="button small" disabled={page <= 1 || disabled} onClick={() => changePage(page - 1)} aria-label="Trang trước">←</button><span>Trang {page} / {totalPages}</span><button className="button small" disabled={page >= totalPages || disabled} onClick={() => changePage(page + 1)} aria-label="Trang sau">→</button></div></div>
  </div>
}
