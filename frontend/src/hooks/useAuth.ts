import { createContext, useContext } from 'react'
import type { AdminUser } from '../services/api'

interface AuthState {
  user: AdminUser | null
  loading: boolean
  error: string
  sessionExpired: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  retry: () => void
}

export const AuthContext = createContext<AuthState | null>(null)
export function useAuth() { return useContext(AuthContext) }
