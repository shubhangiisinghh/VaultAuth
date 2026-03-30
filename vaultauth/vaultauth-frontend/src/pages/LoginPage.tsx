import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthChallenge } from '../auth/useAuthChallenge'
import { useAuth } from '../auth/AuthContext'
import { PasswordForm } from '../components/PasswordForm'
import { TotpInput } from '../components/TotpInput'
import { ChallengeForm } from '../components/ChallengeForm'
import { ForcePasswordChange } from '../components/ForcePasswordChange'
import type { AuthStep } from '../types'

const STEP_LABELS: Record<string, string> = {
  password:         'Sign in',
  TOTP_PENDING:     'Two-factor authentication',
  CHALLENGE_PENDING:'Security question',
  PASSWORD_CHANGE:  'Set new password',
  COMPLETE:         'Welcome',
}

export function LoginPage() {
  const urlStep  = useAuthChallenge()
  const [step, setStep] = useState<AuthStep | 'password'>(urlStep)
  const { refresh, user } = useAuth()
  const navigate = useNavigate()

  // if already logged in, go straight to dashboard
  useEffect(() => {
    if (user) navigate('/dashboard', { replace: true })
  }, [user, navigate])

  // keep step in sync if the URL changes (e.g. browser back)
  useEffect(() => {
    setStep(urlStep)
  }, [urlStep])

  const handleNext = async (nextStep: AuthStep) => {
    if (nextStep === 'COMPLETE') {
      await refresh()
      navigate('/dashboard', { replace: true })
    } else {
      setStep(nextStep)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">
          <span className="logo-icon">🔐</span>
          <span className="logo-text">VaultAuth</span>
        </div>

        <div className="step-indicator">
          {['password', 'TOTP_PENDING', 'COMPLETE'].map((s, i) => (
            <div
              key={s}
              className={`step-dot ${step === s ? 'active' : ''} ${
                i < ['password', 'TOTP_PENDING', 'COMPLETE'].indexOf(step as string) ? 'done' : ''
              }`}
            />
          ))}
        </div>

        <h1 className="login-title">{STEP_LABELS[step] ?? 'Sign in'}</h1>

        {step === 'password'          && <PasswordForm onNext={handleNext} />}
        {step === 'TOTP_PENDING'      && <TotpInput onNext={handleNext} />}
        {step === 'CHALLENGE_PENDING' && <ChallengeForm onNext={handleNext} />}
        {step === 'PASSWORD_CHANGE'   && <ForcePasswordChange onNext={handleNext} />}
      </div>
    </div>
  )
}
