import { useRef, useState } from 'react'
import { submitTotp } from '../api/session'
import type { AuthStep } from '../types'

interface Props {
  onNext: (step: AuthStep) => void
}

export function TotpInput({ onNext }: Props) {
  const [digits, setDigits] = useState(['', '', '', '', '', ''])
  const [error, setError]   = useState('')
  const [loading, setLoading] = useState(false)
  const inputs = useRef<(HTMLInputElement | null)[]>([])

  const handleChange = (index: number, value: string) => {
    // only accept a single digit
    const digit = value.replace(/\D/g, '').slice(-1)
    const next = [...digits]
    next[index] = digit
    setDigits(next)
    setError('')

    if (digit && index < 5) {
      inputs.current[index + 1]?.focus()
    }

    // auto-submit when all 6 digits are filled
    if (digit && index === 5) {
      const code = [...next].join('')
      if (code.length === 6) {
        handleSubmit(code)
      }
    }
  }

  const handleKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      inputs.current[index - 1]?.focus()
    }
  }

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    const next = [...digits]
    pasted.split('').forEach((ch, i) => { next[i] = ch })
    setDigits(next)
    if (pasted.length === 6) {
      handleSubmit(pasted)
    } else {
      inputs.current[pasted.length]?.focus()
    }
  }

  const handleSubmit = async (code: string) => {
    setError('')
    setLoading(true)
    try {
      const res = await submitTotp(code)
      if (res.error) {
        setError(res.error)
        setDigits(['', '', '', '', '', ''])
        inputs.current[0]?.focus()
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
    <div className="totp-wrapper">
      <p className="totp-hint">
        Open your authenticator app and enter the 6-digit code.
      </p>
      <div className="totp-digits" onPaste={handlePaste}>
        {digits.map((d, i) => (
          <input
            key={i}
            ref={el => { inputs.current[i] = el }}
            type="text"
            inputMode="numeric"
            maxLength={1}
            value={d}
            className="totp-digit"
            onChange={e => handleChange(i, e.target.value)}
            onKeyDown={e => handleKeyDown(i, e)}
            disabled={loading}
            autoFocus={i === 0}
          />
        ))}
      </div>
      {error && <p className="form-error">{error}</p>}
      {loading && <p className="form-hint">Verifying…</p>}
    </div>
  )
}
