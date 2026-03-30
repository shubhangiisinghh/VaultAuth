import { useState } from 'react'
import { changePassword } from '../api/session'
import type { AuthStep } from '../types'

interface Props {
  onNext: (step: AuthStep) => void
}

export function ForcePasswordChange({ onNext }: Props) {
  const [password, setPassword]   = useState('')
  const [confirm, setConfirm]     = useState('')
  const [error, setError]         = useState('')
  const [loading, setLoading]     = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }

    setLoading(true)
    try {
      const res = await changePassword(password)
      if (res.error) {
        setError(res.error)
      } else {
        onNext(res.step)
      }
    } catch {
      setError('Something went wrong. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="auth-form">
      <p className="form-hint">
        You need to set a new password before continuing.
      </p>
      <div className="form-group">
        <label htmlFor="new-password">New password</label>
        <input
          id="new-password"
          type="password"
          value={password}
          onChange={e => setPassword(e.target.value)}
          autoFocus
          required
          minLength={8}
        />
      </div>
      <div className="form-group">
        <label htmlFor="confirm-password">Confirm password</label>
        <input
          id="confirm-password"
          type="password"
          value={confirm}
          onChange={e => setConfirm(e.target.value)}
          required
        />
      </div>
      {error && <p className="form-error">{error}</p>}
      <button type="submit" className="btn-primary" disabled={loading}>
        {loading ? 'Saving…' : 'Set new password'}
      </button>
    </form>
  )
}
