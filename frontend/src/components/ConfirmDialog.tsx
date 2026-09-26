import { useEffect, useId, useRef, useState } from 'react'
import { Icon } from './Icon'
export function ConfirmDialog({ title, description, confirmLabel, onConfirm, onClose }: {
  title: string; description: string; confirmLabel: string; onConfirm: () => Promise<void>; onClose: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const titleId = useId()
  const descriptionId = useId()
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const dialog = ref.current
    dialog?.showModal()
    return () => { dialog?.close(); previous?.focus() }
  }, [])
  return <dialog ref={ref} className="confirm-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} onCancel={event => { event.preventDefault(); if (!busy) onClose() }}><div className="empty-icon"><Icon name="shield" size={28} /></div><h2 id={titleId}>{title}</h2><p id={descriptionId}>{description}</p>{error && <p role="alert" className="field-error">{error}</p>}<div className="dialog-actions"><button className="button" disabled={busy} autoFocus onClick={onClose}>Hủy</button><button className="button primary" disabled={busy} onClick={async () => { setBusy(true); setError(''); try { await onConfirm(); onClose() } catch { setError('Thao tác chưa hoàn tất. Vui lòng thử lại.'); setBusy(false) } }}>{busy ? 'Đang cập nhật…' : confirmLabel}</button></div></dialog>
}
