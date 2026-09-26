import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Icon } from './Icon'
import { ToastContext } from '../hooks/useToast'
import type { Notify, ToastTone } from '../hooks/useToast'
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; tone: ToastTone; id: number }>()
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const notify = useCallback<Notify>((message, tone = 'info') => {
    clearTimeout(timer.current)
    setToast({ message, tone, id: Date.now() })
    timer.current = setTimeout(() => setToast(undefined), 6000)
  }, [])
  useEffect(() => () => clearTimeout(timer.current), [])
  return <ToastContext.Provider value={notify}>{children}<div className="toast-region" aria-live="polite" aria-atomic="true">{toast && <div key={toast.id} className={`toast ${toast.tone}`} role={toast.tone === 'error' ? 'alert' : 'status'}><Icon name={toast.tone === 'success' ? 'check' : 'info'} /><span>{toast.message}</span><button className="icon-button" aria-label="Đóng thông báo" onClick={() => setToast(undefined)}><Icon name="close" size={16} /></button></div>}</div></ToastContext.Provider>
}
