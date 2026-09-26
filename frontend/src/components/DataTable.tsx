import { useState } from 'react'
import type { ReactNode } from 'react'
import { paginate } from '../utils/format'
import { EmptyState, LoadingState } from './Common'
export interface Column<T> { key: string; label: string; render: (row: T) => ReactNode }
export function DataTable<T extends { id: string }>({ rows, columns, loading = false, unavailable = false, failed = false, filtered = false, label }: {
  rows: T[]; columns: Column<T>[]; loading?: boolean; unavailable?: boolean; failed?: boolean; filtered?: boolean; label: string
}) {
  const [requestedPage, setPage] = useState(1)
  const { page, totalPages, rows: visible } = paginate(rows, requestedPage)
  return <div aria-busy={loading}>
    <div className="table-scroll"><table><caption className="sr-only">{label}</caption><thead><tr>{columns.map(column => <th key={column.key} scope="col">{column.label}</th>)}</tr></thead><tbody>{!loading && visible.map(row => <tr key={row.id}>{columns.map(column => <td key={column.key}>{column.render(row)}</td>)}</tr>)}</tbody></table></div>
    {loading ? <LoadingState /> : !visible.length && <EmptyState title={failed ? 'Dữ liệu tạm thời không khả dụng' : unavailable ? 'Chưa có nguồn dữ liệu' : filtered ? 'Không tìm thấy kết quả' : 'Chưa có dữ liệu'} description={failed ? 'Hãy thử tải lại bằng nút Refresh phía trên.' : unavailable ? 'Danh sách sẽ được cập nhật khi backend cung cấp API tương ứng.' : filtered ? 'Thử từ khóa khác hoặc đặt lại bộ lọc.' : 'Các bản ghi sẽ xuất hiện tại đây khi hệ thống nhận được dữ liệu.'} />}
    <div className="table-footer"><span>{unavailable || failed || loading ? 'Chưa có dữ liệu để phân trang' : `${rows.length ? (page - 1) * 10 + 1 : 0}–${Math.min(page * 10, rows.length)} trên ${rows.length} bản ghi`}</span><div><button className="button small" disabled={page <= 1 || loading} onClick={() => setPage(page - 1)} aria-label="Trang trước">←</button><span>Trang {page} / {totalPages}</span><button className="button small" disabled={page >= totalPages || loading} onClick={() => setPage(page + 1)} aria-label="Trang sau">→</button></div></div>
  </div>
}
