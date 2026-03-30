import { useState } from 'react'
import { submitChallenge } from '../api/session'
import type { AuthStep } from '../types'

interface Props {
  onNext: (step: AuthStep) => void
}

export function ChallengeForm({ onNext }: Props) {
  const [answer, setAnswer]   = useState('')
  const [error, setError]     = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await submitChallenge(answer)
      if (res.error) {
        setError(res.error)
        setAnswer('')
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
        Answer your security question to continue.
      </p>
      <div className="form-group">
        <label htmlFor="answer">Security answer</label>
        <input
          id="answer"
          type="password"
          value={answer}
          onChange={e => setAnswer(e.target.value)}
          autoFocus
          required
        />
      </div>
      {error && <p className="form-error">{error}</p>}
      <button type="submit" className="btn-primary" disabled={loading}>
        {loading ? 'Verifying…' : 'Continue'}
      </button>
    </form>
  )
}
