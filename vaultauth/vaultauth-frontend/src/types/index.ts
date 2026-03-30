export type UserRole = 'ADMIN' | 'CLINICIAN' | 'VIEWER'

export type AuthStep =
  | 'COMPLETE'
  | 'TOTP_PENDING'
  | 'CHALLENGE_PENDING'
  | 'PASSWORD_CHANGE'

export interface SessionUser {
  id: number
  username: string
  displayName: string
  email: string
  role: UserRole
  twoFactorEnabled: boolean
}

export interface AuthState {
  user: SessionUser | null
  loading: boolean
}
