import React, { createContext, useContext, useEffect, useState } from 'react'
import { fetchSession, logout as apiLogout } from '../api/session'
import type { SessionUser } from '../types'

interface AuthContextValue {
  user: SessionUser | null
  loading: boolean
  refresh: () => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = async () => {
    const u = await fetchSession()
    setUser(u)
  }

  const logout = async () => {
    await apiLogout()
    setUser(null)
    window.location.href = '/login'
  }

  useEffect(() => {
    refresh().finally(() => setLoading(false))
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, refresh, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
