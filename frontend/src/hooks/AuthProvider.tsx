import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { protectionApi } from '../services/api'
import type { AdminUser } from '../services/api'
import { ApiError, clearCsrfToken } from '../services/http'
import { AuthContext } from './useAuth'

export function AuthProvider({ demo, children }: { demo: boolean; children: ReactNode }) {
  const [user, setUser] = useState<AdminUser | null>(null)
  const [loading, setLoading] = useState(!demo)
  const [error, setError] = useState('')
  const [sessionExpired, setSessionExpired] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const sessionCheck = useRef<AbortController | null>(null)

  useEffect(() => {
    if (demo) return
    const controller = new AbortController()
    sessionCheck.current = controller
    async function checkSession() {
      try {
        const admin = await protectionApi.getCurrentAdmin(controller.signal)
        if (!controller.signal.aborted) setUser(admin)
      } catch (problem) {
        if (controller.signal.aborted) return
        setUser(null)
        if (!(problem instanceof ApiError) || problem.status !== 401) {
          setError(problem instanceof Error ? problem.message : 'Không kiểm tra được phiên Admin.')
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    void checkSession()
    return () => controller.abort()
  }, [demo, attempt])

  useEffect(() => {
    function expire() {
      if (demo) return
      // Không để kết quả kiểm tra phiên cũ đăng nhập lại sau khi đã nhận 401.
      sessionCheck.current?.abort()
      clearCsrfToken()
      setUser(null)
      setLoading(false)
      setError('')
      setSessionExpired(true)
      window.location.hash = '/login'
    }
    window.addEventListener('usbshield:session-expired', expire)
    return () => window.removeEventListener('usbshield:session-expired', expire)
  }, [demo])

  async function login(username: string, password: string) {
    if (demo) throw new Error('Demo chỉ cho phép xem dữ liệu.')
    const admin = await protectionApi.login(username, password)
    setUser(admin)
    setError('')
    setSessionExpired(false)
  }

  async function logout() {
    await protectionApi.logout()
    setUser(null)
    setSessionExpired(false)
    window.location.hash = '/login'
  }

  function retry() {
    setLoading(true)
    setError('')
    setAttempt(previous => previous + 1)
  }

  return <AuthContext.Provider value={{ user, loading, error, sessionExpired, login, logout, retry }}>{children}</AuthContext.Provider>
}
