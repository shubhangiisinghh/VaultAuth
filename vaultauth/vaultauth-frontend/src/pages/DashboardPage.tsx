import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getTotpSetup, confirmTotpEnrolment } from '../api/session'
import { TotpInput } from '../components/TotpInput'
import type { AuthStep } from '../types'

export function DashboardPage() {
  const { user, logout } = useAuth()
  const [qrCode, setQrCode]       = useState<string | null>(null)
  const [enrolling, setEnrolling] = useState(false)
  const [enrolled, setEnrolled]   = useState(false)
  const [error, setError]         = useState('')

  const startEnrolment = async () => {
    setError('')
    try {
      const res = await getTotpSetup()
      setQrCode(res.qrCode)
      setEnrolling(true)
    } catch {
      setError('Failed to start TOTP setup. Please try again.')
    }
  }

  // For enrolment confirmation we reuse TotpInput but intercept the submit
  const handleEnrolmentCode = async (code: string): Promise<{ step: AuthStep; error?: string }> => {
    const res = await confirmTotpEnrolment(code)
    if (res.error) return { step: 'TOTP_PENDING', error: res.error }
    setEnrolling(false)
    setEnrolled(true)
    setQrCode(null)
    return { step: 'COMPLETE' }
  }

  if (!user) return null

  return (
    <div className="page">
      <header className="page-header">
        <div className="header-left">
          <span className="logo-icon">🔐</span>
          <span className="logo-text">VaultAuth</span>
        </div>
        <div className="header-right">
          <span className="user-chip">{user.displayName}</span>
          <span className="role-badge">{user.role}</span>
          <button className="btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </header>

      <main className="page-main">
        <div className="welcome-card">
          <h2>Welcome back, {user.displayName.split(' ')[0]}</h2>
          <p>You are signed in as <strong>{user.username}</strong> with role <strong>{user.role}</strong>.</p>
        </div>

        <div className="security-card">
          <h3>Security settings</h3>

          {user.twoFactorEnabled || enrolled ? (
            <div className="status-row">
              <span className="status-dot green" />
              <span>Two-factor authentication is <strong>enabled</strong> on your account.</span>
            </div>
          ) : (
            <div>
              <div className="status-row">
                <span className="status-dot amber" />
                <span>Two-factor authentication is <strong>not enabled</strong>.</span>
              </div>

              {!enrolling && (
                <button className="btn-primary" style={{ marginTop: 16 }} onClick={startEnrolment}>
                  Enable authenticator app
                </button>
              )}

              {enrolling && qrCode && (
                <div className="enrolment-box">
                  <p>Scan this QR code with Google Authenticator or Authy, then enter the 6-digit code below to confirm.</p>
                  <img src={qrCode} alt="TOTP QR code" className="qr-code" />
                  <TotpInput onNext={(_step) => {}} />
                </div>
              )}
            </div>
          )}

          {error && <p className="form-error" style={{ marginTop: 12 }}>{error}</p>}
        </div>
      </main>
    </div>
  )
}
