import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { protectionApi } from '../services/api'
import type { AdminUser } from '../services/api'
import { ApiError } from '../services/http'
import { AuthContext } from './useAuth'

export function AuthProvider({ demo, children }: { demo: boolean; children: ReactNode }) {
  const [user, setUser] = useState<AdminUser | null>(null)
  const [loading, setLoading] = useState(!demo)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (demo) return
    const controller = new AbortController()
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
    function expire() { setUser(null); setError(''); window.location.hash = '/login' }
    window.addEventListener('usbshield:session-expired', expire)
    return () => window.removeEventListener('usbshield:session-expired', expire)
  }, [])

  async function login(username: string, password: string) {
    if (demo) throw new Error('Demo chỉ cho phép xem dữ liệu.')
    const admin = await protectionApi.login(username, password)
    setUser(admin)
    setError('')
  }

  async function logout() {
    await protectionApi.logout()
    setUser(null)
    window.location.hash = '/login'
  }

  function retry() {
    setLoading(true)
    setError('')
    setAttempt(attempt + 1)
  }

  return <AuthContext.Provider value={{ user, loading, error, login, logout, retry }}>{children}</AuthContext.Provider>
}
