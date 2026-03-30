import { authFetch, post } from './authClient'
import type { SessionUser, AuthStep } from '../types'

export async function fetchSession(): Promise<SessionUser | null> {
  try {
    const res = await authFetch('/auth/session', { skipAuthRedirect: true })
    if (res.status === 401) return null
    return res.json()
  } catch {
    return null
  }
}

export async function login(username: string, password: string): Promise<{ step: AuthStep; error?: string }> {
  return post('/auth/login', { username, password })
}

export async function submitTotp(code: string): Promise<{ step: AuthStep; error?: string }> {
  return post('/auth/totp', { code })
}

export async function submitChallenge(answer: string): Promise<{ step: AuthStep; error?: string }> {
  return post('/auth/challenge', { answer })
}

export async function changePassword(password: string): Promise<{ step: AuthStep; error?: string }> {
  return post('/auth/password', { password })
}

export async function logout(): Promise<void> {
  await post('/auth/logout', {})
}

export async function getTotpSetup(): Promise<{ qrCode: string }> {
  const res = await authFetch('/auth/totp/setup')
  return res.json()
}

export async function confirmTotpEnrolment(code: string): Promise<{ message?: string; error?: string }> {
  return post('/auth/totp/confirm', { code })
}
