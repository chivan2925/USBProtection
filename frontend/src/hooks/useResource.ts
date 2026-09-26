import { useCallback, useEffect, useRef, useState } from 'react'
import { BackendUnavailableError } from '../services/api'

export type ResourceState<T> =
  | { status: 'loading'; data?: never; error?: never }
  | { status: 'ready'; data: T; error?: never }
  | { status: 'unavailable'; data?: never; error?: never }
  | { status: 'error'; data?: never; error: string }

export function useResource<T>(loader: (signal: AbortSignal) => Promise<T>) {
  const [state, setState] = useState<ResourceState<T>>({ status: 'loading' })
  const [updatedAt, setUpdatedAt] = useState<string>()
  const active = useRef<AbortController | null>(null)
  const refresh = useCallback(async () => {
    active.current?.abort()
    const controller = new AbortController()
    active.current = controller
    setState({ status: 'loading' })
    try {
      const data = await loader(controller.signal)
      if (controller.signal.aborted) return 'cancelled' as const
      setState({ status: 'ready', data })
      setUpdatedAt(new Date().toISOString())
      return 'ready' as const
    } catch (error) {
      if (controller.signal.aborted) return 'cancelled' as const
      if (error instanceof BackendUnavailableError) {
        setState({ status: 'unavailable' })
        return 'unavailable' as const
      }
      setState({ status: 'error', error: 'Không thể tải dữ liệu. Vui lòng thử lại hoặc kiểm tra kết nối với máy chủ.' })
      return 'error' as const
    }
  }, [loader])
  useEffect(() => {
    // Start the external read after the effect has been committed. StrictMode
    // cleanup cancels the scheduled read as well as any in-flight request.
    const initialRead = setTimeout(() => { void refresh() }, 0)
    return () => { clearTimeout(initialRead); active.current?.abort() }
  }, [refresh])
  return { state, refresh, updatedAt }
}
