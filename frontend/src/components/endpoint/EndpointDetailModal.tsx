import { useCallback, useEffect, useRef } from 'react'
import { Icon } from '../Icon'
import { EndpointDetail } from '../../pages/Endpoints'
import { useResource } from '../../hooks/useResource'
import { useToast } from '../../hooks/useToast'
import { protectionApi } from '../../services/api'
import { getDemoSnapshot } from '../../services/demo'
import { formatDate } from '../../utils/format'

export function EndpointDetailModal({ endpointId, demo, onClose, onPolicyChange }: {
  endpointId: string; demo: boolean; onClose: () => void; onPolicyChange: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const loader = useCallback((signal: AbortSignal) => demo
    ? getDemoSnapshot(signal)
    : protectionApi.getSnapshot(signal, endpointId), [demo, endpointId])
  const { state, refresh, updatedAt } = useResource(loader)
  const notify = useToast()

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    const dialog = dialogRef.current
    dialog?.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      dialog?.close()
      document.body.style.overflow = overflow
      const target = previous?.isConnected ? previous : document.querySelector<HTMLElement>('#main-content')
      target?.focus({ preventScroll: true })
    }
  }, [])

  async function handleRefresh() {
    const result = await refresh()
    if (result === 'ready') notify(demo ? 'Đã tải lại dữ liệu mock.' : 'Đã cập nhật dữ liệu mới nhất.', 'success')
    if (result === 'error') notify('Tải dữ liệu thất bại. Vui lòng thử lại.', 'error')
  }

  return <dialog ref={dialogRef} className="endpoint-detail-modal" aria-label="Chi tiết endpoint"
    onCancel={event => { event.preventDefault(); onClose() }}>
    <EndpointDetail embedded state={state} endpointId={endpointId} demo={demo}
      refresh={() => { void handleRefresh() }} onPolicyChange={onPolicyChange}
      refreshButton={<div className="endpoint-modal-actions">
        <div className="refresh-actions">
          {updatedAt && state.status === 'ready' && <span>Cập nhật: {formatDate(updatedAt)}</span>}
          <button className="button" disabled={state.status === 'loading'} onClick={() => void handleRefresh()}>
            <Icon name="refresh" size={16} className={state.status === 'loading' ? 'spin' : ''} />Refresh dữ liệu
          </button>
        </div>
        <button className="button" autoFocus onClick={onClose} aria-label="Đóng chi tiết endpoint"><Icon name="close" size={16} />Đóng</button>
      </div>} />
  </dialog>
}
